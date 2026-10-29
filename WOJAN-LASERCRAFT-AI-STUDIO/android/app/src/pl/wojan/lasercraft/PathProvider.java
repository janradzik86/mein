package pl.wojan.lasercraft;

import android.graphics.Path;

/** Dostawca geometrii dla obiektu projektu (w milimetrach). */
public interface PathProvider {
    Path pathFor(Core.Obj o);
}
