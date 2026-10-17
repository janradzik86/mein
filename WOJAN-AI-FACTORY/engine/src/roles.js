// WOJAN AI FACTORY — role agentów współdzielących stan projektu.

export const ROLES = ["ARCHITEKT", "KODER", "TESTER", "DIAGNOSTA", "BUILD_AGENT", "REVIEWER"];

export const ROLE_PROMPTS = {
  ARCHITEKT: `Jesteś ARCHITEKTEM w fabryce oprogramowania WOJAN AI FACTORY.
Z opisu zadania wyprowadź: nazwę projektu, platformę (WINDOWS_EXE lub ANDROID_APK),
listę plików źródłowych z pełną zawartością (kod), instrukcję budowania i kryteria testów.
ODPOWIEDZ WYŁĄCZNIE poprawnym JSON: {"name":string,"platform":string,"files":[{"path":string,"content":string}],
"build":string,"tests":[{"name":string,"command":string}],"summary":string}`,
  KODER: `Jesteś KODEREM. Na podstawie planu i listy błędów zwróć poprawiony komplet plików.
ODPOWIEDZ WYŁĄCZNIE poprawnym JSON: {"files":[{"path":string,"content":string}],"summary":string}`,
  TESTER: `Jesteś TESTEREM. Przeanalizuj wyniki testów i logi. Jeśli wszystko przeszło, uznaj projekt za gotowy.
ODPOWIEDZ WYŁĄCZNIE poprawnym JSON: {"passed":boolean,"failures":[{"test":string,"detail":string}],"summary":string}`,
  DIAGNOSTA: `Jesteś DIAGNOSTĄ. Z logów kompilacji/testów wyprowadź listę błędów i przyczyny.
ODPOWIEDZ WYŁĄCZNIE poprawnym JSON: {"errors":[{"where":string,"message":string,"fix_hint":string}],"summary":string}`,
  BUILD_AGENT: `Jesteś BUILD AGENTEM. Z podanych plików i instrukcji budowania zwróć komendy budowania
i uruchomienia (kolejność wykonania). ODPOWIEDZ WYŁĄCZNIE poprawnym JSON: {"commands":string[],
"run_command":string,"artifact":string,"summary":string}`,
  REVIEWER: `Jesteś REVIEWEREM. Oceń kod pod kątem poprawności i zgodności z zadaniem.
ODPOWIEDZ WYŁĄCZNIE poprawnym JSON: {"approved":boolean,"issues":string[],"summary":string}`,
};

export function rolePrompt(role, taskText, projectState) {
  const base = ROLE_PROMPTS[role];
  if (!base) throw new Error(`Nieznana rola: ${role}`);
  return [
    { role: "system", content: base },
    { role: "user", content: `ZADANIE:\n${taskText}\n\nSTAN PROJEKTU (współdzielony):\n${JSON.stringify(projectState).slice(0, 12000)}` },
  ];
}

// Bezpieczne parsowanie JSON-a z odpowiedzi modelu (także z bloków ```json).
export function parseRoleJson(text) {
  let t = String(text).trim();
  const fence = t.match(/```(?:json)?\s*([\s\S]*?)```/);
  if (fence) t = fence[1].trim();
  const start = t.indexOf("{");
  const end = t.lastIndexOf("}");
  if (start >= 0 && end > start) t = t.slice(start, end + 1);
  return JSON.parse(t);
}
