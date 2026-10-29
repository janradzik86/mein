package pl.wojan.lasercraft;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/**
 * Podgląd/edytor projektu: siatka mm, linijki, przesuwanie i skalowanie palcami,
 * zaznaczanie obiektów, przyciąganie do siatki i środka, kolory warstw produkcyjnych.
 */
public class EditorView extends View {

    public interface Listener {
        void onChanged(String label);

        void onSelected(Core.Obj o);
    }

    private Core.Design d;
    private final PathProvider prov;
    private Listener listener;
    private float pxmm = 3f;
    private float panX = 0, panY = 0;
    private int sel = -1;
    private boolean grid = true;
    private boolean snap = true;
    private boolean guides = false;
    private float dragStartX, dragStartY;
    private double objStartX, objStartY;
    private boolean dragging = false;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestures;
    private Bitmap cache;
    private boolean cacheValid = false;
    private float lastTouchDist = 0;

    public EditorView(Context c, Core.Design design, PathProvider provider) {
        super(c);
        this.d = design;
        this.prov = provider;
        textPaint.setTextSize(Ui.dp(c, 9));
        textPaint.setColor(Ui.MUT);
        setBackgroundColor(Color.rgb(0x0E, 0x10, 0x13));
        scaleDetector = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            public boolean onScale(ScaleGestureDetector det) {
                pxmm *= det.getScaleFactor();
                pxmm = Math.max(0.4f, Math.min(40f, pxmm));
                cacheValid = false;
                invalidate();
                return true;
            }
        });
        gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            public boolean onDoubleTap(MotionEvent e) {
                fit();
                return true;
            }

            public boolean onSingleTapConfirmed(MotionEvent e) {
                selectAt(toMmX(e.getX()), toMmY(e.getY()));
                return true;
            }
        });
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setDesign(Core.Design design) {
        d = design;
        sel = -1;
        cacheValid = false;
        invalidate();
    }

    public void setGrid(boolean g) {
        grid = g;
        invalidate();
    }

    public void setSnap(boolean s) {
        snap = s;
    }

    public void setGuides(boolean g) {
        guides = g;
        cacheValid = false;
        invalidate();
    }

    public int selectedIndex() {
        return sel;
    }

    public Core.Obj selected() {
        if (sel < 0 || sel >= d.objects.size()) return null;
        return d.objects.get(sel);
    }

    public void selectIndex(int i) {
        sel = i;
        if (listener != null) listener.onSelected(selected());
        invalidate();
    }

    void fit() {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float pad = Ui.dp(getContext(), 34);
        float kx = (w - pad * 2) / (float) d.product.w;
        float ky = (h - pad * 2) / (float) d.product.h;
        pxmm = Math.max(0.4f, Math.min(kx, ky));
        panX = (w - (float) d.product.w * pxmm) / 2f;
        panY = (h - (float) d.product.h * pxmm) / 2f;
        cacheValid = false;
        invalidate();
    }

    private float toMmX(float x) {
        return (x - panX) / pxmm;
    }

    private float toMmY(float y) {
        return (y - panY) / pxmm;
    }

    private float toPxX(double mm) {
        return panX + (float) mm * pxmm;
    }

    private float toPxY(double mm) {
        return panY + (float) mm * pxmm;
    }

    public void selectAt(float mmx, float mmy) {
        int hit = -1;
        for (int i = d.objects.size() - 1; i >= 0; i--) {
            Core.Obj o = d.objects.get(i);
            if (!o.visible || o.locked) continue;
            Path p = prov.pathFor(o);
            if (p == null || p.isEmpty()) {
                if ("raster".equals(o.type) && mmx >= o.x && mmy >= o.y && mmx <= o.x + o.imgW && mmy <= o.y + o.imgH) {
                    hit = i;
                    break;
                }
                continue;
            }
            RectF b = new RectF();
            p.computeBounds(b, true);
            float tol = Math.max(1.2f, 3f / pxmm);
            RectF big = new RectF(b.left - tol, b.top - tol, b.right + tol, b.bottom + tol);
            if (big.contains(mmx, mmy)) {
                hit = i;
                break;
            }
            if ("fill".equals(o.role) && b.contains(mmx, mmy)) {
                hit = i;
                break;
            }
        }
        sel = hit;
        if (listener != null) listener.onSelected(selected());
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        scaleDetector.onTouchEvent(e);
        gestures.onTouchEvent(e);
        int action = e.getActionMasked();
        if (e.getPointerCount() > 1) {
            dragging = false;
            return true;
        }
        if (action == MotionEvent.ACTION_DOWN) {
            dragStartX = e.getX();
            dragStartY = e.getY();
            Core.Obj o = selected();
            dragging = o != null && !o.locked;
            if (dragging) {
                objStartX = o.x;
                objStartY = o.y;
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            float dx = e.getX() - dragStartX;
            float dy = e.getY() - dragStartY;
            if (dragging && selected() != null && Math.abs(dx) + Math.abs(dy) > Ui.dp(getContext(), 3)) {
                Core.Obj o = selected();
                double nx = objStartX + dx / pxmm;
                double ny = objStartY + dy / pxmm;
                if (snap) {
                    nx = Math.round(nx * 2.0) / 2.0;
                    ny = Math.round(ny * 2.0) / 2.0;
                    if (Math.abs(nx - d.product.w / 2) < 0.8) nx = d.product.w / 2;
                    if (Math.abs(ny - d.product.h / 2) < 0.8) ny = d.product.h / 2;
                }
                o.x = nx;
                o.y = ny;
                cacheValid = false;
                invalidate();
            } else if (!dragging) {
                panX += e.getX() - dragStartX;
                panY += e.getY() - dragStartY;
                dragStartX = e.getX();
                dragStartY = e.getY();
                cacheValid = false;
                invalidate();
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (dragging && selected() != null && listener != null
                    && (Math.abs(selected().x - objStartX) > 1e-6 || Math.abs(selected().y - objStartY) > 1e-6))
                listener.onChanged("Przesunięcie: " + selected().name);
            dragging = false;
        }
        return true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        fit();
    }

    @Override
    protected void onDraw(Canvas cv) {
        super.onDraw(cv);
        if (d == null) return;
        float W = (float) d.product.w * pxmm, H = (float) d.product.h * pxmm;

        // obszar produktu
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(0xFA, 0xFA, 0xF7));
        RectF area = new RectF(panX, panY, panX + W, panY + H);
        cv.drawRect(area, paint);

        // siatka mm
        if (grid) {
            paint.setStyle(Paint.Style.STROKE);
            float stepMm = pxmm > 12 ? 1f : (pxmm > 5 ? 5f : 10f);
            paint.setStrokeWidth(1f);
            for (float x = 0; x <= d.product.w + 0.01f; x += stepMm) {
                boolean major = Math.abs(x % 10f) < 0.001f;
                paint.setColor(major ? Color.rgb(0xC9, 0xD2, 0xDC) : Color.rgb(0xE6, 0xEA, 0xEE));
                cv.drawLine(toPxX(x), panY, toPxX(x), panY + H, paint);
            }
            for (float y = 0; y <= d.product.h + 0.01f; y += stepMm) {
                boolean major = Math.abs(y % 10f) < 0.001f;
                paint.setColor(major ? Color.rgb(0xC9, 0xD2, 0xDC) : Color.rgb(0xE6, 0xEA, 0xEE));
                cv.drawLine(panX, toPxY(y), panX + W, toPxY(y), paint);
            }
        }

        // obiekty
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        for (int li = 0; li < Core.LAYERS.length; li++) {
            String layer = Core.LAYERS[li];
            if (Core.L_GUIDES.equals(layer) && !guides) continue;
            if (Core.L_ENGRAVE_RASTER.equals(layer)) {
                drawRasters(cv);
                continue;
            }
            for (int k = 0; k < d.objects.size(); k++) {
                Core.Obj o = d.objects.get(k);
                if (!o.visible || !layer.equals(o.layer)) continue;
                Path p = prov.pathFor(o);
                if (p == null || p.isEmpty()) continue;
                int col = layerColorInt(layer);
                paint.setColor(col);
                boolean fill = "fill".equals(o.role) || Core.L_CUT_OUTER.equals(layer);
                paint.setStyle(fill && !Core.L_CUT_OUTER.equals(layer) ? Paint.Style.FILL : Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1f, (float) Math.max(0.1, o.strokeW) * pxmm));
                if (Core.L_GUIDES.equals(layer)) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(1.2f);
                    paint.setPathEffect(new DashPathEffect(new float[]{6f, 4f}, 0));
                } else {
                    paint.setPathEffect(null);
                }
                cv.save();
                cv.translate(panX, panY);
                cv.scale(pxmm, pxmm);
                cv.drawPath(p, paint);
                cv.restore();
            }
        }
        paint.setPathEffect(null);

        // zaznaczenie
        Core.Obj so = selected();
        if (so != null) {
            RectF b = boundsOf(so);
            if (b != null) {
                RectF r = new RectF(toPxX(b.left), toPxY(b.top), toPxX(b.right), toPxY(b.bottom));
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(Ui.ACC);
                paint.setStrokeWidth(2f);
                paint.setPathEffect(new DashPathEffect(new float[]{8f, 5f}, 0));
                cv.drawRect(r, paint);
                paint.setPathEffect(null);
            }
        }

        // ramka obszaru + linijki
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        paint.setColor(Color.rgb(0x55, 0x5F, 0x6B));
        cv.drawRect(area, paint);
        drawRulers(cv, W, H);

        // skala i info
        textPaint.setColor(Ui.TXT);
        textPaint.setTextSize(Ui.dp(getContext(), 11));
        String info = String.format(java.util.Locale.US, "%.2f mm/px  •  %.0f%%  •  siatka %.0f mm",
                pxmm, pxmm * 100f / 3f, pxmm > 12 ? 1f : (pxmm > 5 ? 5f : 10f));
        cv.drawText(info, Ui.dp(getContext(), 8), getHeight() - Ui.dp(getContext(), 8), textPaint);
        if (so != null) {
            textPaint.setColor(Ui.ACC);
            String s = so.name + String.format(java.util.Locale.US, "  x=%.1f y=%.1f", so.x, so.y)
                    + (so.type.equals("text") ? "  " + Core.fmt(so.size) + " mm" : "");
            cv.drawText(s, Ui.dp(getContext(), 8), Ui.dp(getContext(), 14), textPaint);
        }
    }

    private void drawRasters(Canvas cv) {
        for (int k = 0; k < d.objects.size(); k++) {
            Core.Obj o = d.objects.get(k);
            if (!o.visible || !Core.L_ENGRAVE_RASTER.equals(o.layer)) continue;
            java.io.File f = Store.rasterFile(getContext(), d.id, o.d);
            if (!f.exists()) continue;
            Bitmap b = android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath());
            if (b == null) continue;
            RectF dst = new RectF(toPxX(o.x), toPxY(o.y), toPxX(o.x + o.imgW), toPxY(o.y + o.imgH));
            cv.drawBitmap(b, null, dst, new Paint(Paint.FILTER_BITMAP_FLAG));
            b.recycle();
        }
    }

    private void drawRulers(Canvas cv, float W, float H) {
        int ph = (int) panY;
        int pw = (int) panX;
        textPaint.setTextSize(Ui.dp(getContext(), 8));
        textPaint.setColor(Color.rgb(0x7E, 0x8A, 0x99));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(0x1A, 0x1E, 0x23));
        cv.drawRect(0, ph - Ui.dp(getContext(), 14), getWidth(), ph, paint);
        cv.drawRect(pw - Ui.dp(getContext(), 26), 0, pw, getHeight(), paint);
        float step = pxmm > 8 ? 10 : (pxmm > 3 ? 20 : 50);
        for (float x = 0; x <= d.product.w; x += step)
            cv.drawText(Core.fmt(x), toPxX(x) + 2, ph - Ui.dp(getContext(), 4), textPaint);
        for (float y = 0; y <= d.product.h; y += step)
            cv.drawText(Core.fmt(y), Math.max(2, pw - Ui.dp(getContext(), 24)), toPxY(y) - 3, textPaint);
    }

    public RectF boundsOf(Core.Obj o) {
        if (o == null) return null;
        if ("raster".equals(o.type) || Core.L_ENGRAVE_RASTER.equals(o.layer))
            return new RectF((float) o.x, (float) o.y, (float) (o.x + o.imgW), (float) (o.y + o.imgH));
        Path p = prov.pathFor(o);
        if (p == null || p.isEmpty()) return null;
        RectF b = new RectF();
        p.computeBounds(b, true);
        return b;
    }

    static int layerColorInt(String layer) {
        String c = Core.layerColor(layer);
        try {
            return Color.parseColor(c);
        } catch (Exception e) {
            return Color.BLACK;
        }
    }

    public Bitmap snapshot(float pxPerMm, boolean showGuides) {
        return Draw.render(getContext(), d, pxPerMm, showGuides);
    }
}
