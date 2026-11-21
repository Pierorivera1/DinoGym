// 16×16 pixel icons, copied from the app's drawables (android/app/src/main/res/drawable/ic_px_*.xml).
// Keys are the app's ExerciseIcon names (decision 30); an unknown key uses GENERIC.

export const exerciseIcons: Record<string, string> = {
  PULL_UP: "M0,0h16v1h-16zM1,1h2v1h-2zM13,1h2v1h-2zM5,2h2v1h-2zM9,2h2v1h-2zM5,3h1v1h-1zM7,3h2v1h-2zM10,3h1v1h-1zM7,4h2v1h-2zM6,5h4v1h-4zM5,6h1v1h-1zM7,6h2v1h-2zM10,6h1v1h-1zM7,7h2v1h-2zM7,8h2v1h-2zM6,9h1v1h-1zM9,9h1v1h-1zM6,10h1v1h-1zM9,10h1v1h-1zM5,11h2v1h-2zM9,11h2v1h-2z",
  ROW: "M3,1h8v1h-8zM4,2h1v1h-1zM9,2h1v1h-1zM4,3h1v1h-1zM9,3h1v1h-1zM1,4h2v1h-2zM4,4h6v1h-6zM1,5h2v1h-2zM5,5h8v1h-8zM8,6h7v1h-7zM12,7h3v1h-3zM13,8h2v1h-2zM14,9h1v1h-1zM14,10h1v1h-1zM0,11h16v1h-16z",
  CURL: "M2,2h2v1h-2zM9,2h2v1h-2zM2,3h9v1h-9zM2,4h2v1h-2zM6,4h2v1h-2zM9,4h2v1h-2zM6,5h2v1h-2zM6,6h2v1h-2zM6,7h2v1h-2zM6,8h2v1h-2zM9,8h3v1h-3zM6,9h8v1h-8zM6,10h9v1h-9zM6,11h9v1h-9z",
  DIP: "M7,1h2v1h-2zM7,2h2v1h-2zM4,3h8v1h-8zM4,4h1v1h-1zM7,4h2v1h-2zM11,4h1v1h-1zM4,5h1v1h-1zM7,5h2v1h-2zM11,5h1v1h-1zM1,6h4v1h-4zM7,6h2v1h-2zM11,6h4v1h-4zM1,7h1v1h-1zM7,7h2v1h-2zM14,7h1v1h-1zM1,8h1v1h-1zM6,8h1v1h-1zM9,8h1v1h-1zM14,8h1v1h-1zM1,9h1v1h-1zM6,9h1v1h-1zM9,9h1v1h-1zM14,9h1v1h-1zM1,10h1v1h-1zM5,10h2v1h-2zM9,10h2v1h-2zM14,10h1v1h-1zM1,11h1v1h-1zM14,11h1v1h-1zM1,12h1v1h-1zM14,12h1v1h-1zM0,13h3v1h-3zM13,13h3v1h-3z",
  PUSH_UP: "M1,4h2v1h-2zM1,5h2v1h-2zM2,6h5v1h-5zM2,7h1v1h-1zM6,7h5v1h-5zM2,8h1v1h-1zM10,8h5v1h-5zM2,9h1v1h-1zM14,9h2v1h-2zM2,10h1v1h-1zM15,10h1v1h-1zM1,11h2v1h-2zM15,11h1v1h-1zM0,12h16v1h-16z",
  RAISE: "M7,1h2v1h-2zM7,2h2v1h-2zM0,3h2v1h-2zM6,3h4v1h-4zM14,3h2v1h-2zM0,4h16v1h-16zM0,5h2v1h-2zM6,5h4v1h-4zM14,5h2v1h-2zM6,6h4v1h-4zM6,7h4v1h-4zM6,8h4v1h-4zM6,9h1v1h-1zM9,9h1v1h-1zM6,10h1v1h-1zM9,10h1v1h-1zM6,11h1v1h-1zM9,11h1v1h-1zM5,12h2v1h-2zM9,12h2v1h-2z",
  SQUAT: "M7,1h2v1h-2zM7,2h2v1h-2zM2,3h12v1h-12zM6,4h4v1h-4zM6,5h4v1h-4zM6,6h4v1h-4zM4,7h8v1h-8zM3,8h2v1h-2zM11,8h2v1h-2zM2,9h2v1h-2zM12,9h2v1h-2zM2,10h2v1h-2zM12,10h2v1h-2zM3,11h2v1h-2zM11,11h2v1h-2zM3,12h2v1h-2zM11,12h2v1h-2zM2,13h3v1h-3zM11,13h3v1h-3z",
  LEVER: "M0,0h16v1h-16zM1,1h2v1h-2zM13,1h2v1h-2zM5,2h2v1h-2zM5,3h2v1h-2zM5,4h2v1h-2zM2,5h2v1h-2zM5,5h2v1h-2zM2,6h12v1h-12zM5,7h10v1h-10z",
  JUMP_ROPE: "M5,1h6v1h-6zM3,2h2v1h-2zM11,2h2v1h-2zM2,3h1v1h-1zM7,3h2v1h-2zM13,3h1v1h-1zM1,4h1v1h-1zM7,4h2v1h-2zM14,4h1v1h-1zM1,5h1v1h-1zM5,5h6v1h-6zM14,5h1v1h-1zM0,6h1v1h-1zM4,6h1v1h-1zM6,6h4v1h-4zM11,6h1v1h-1zM15,6h1v1h-1zM0,7h1v1h-1zM3,7h1v1h-1zM6,7h4v1h-4zM12,7h1v1h-1zM15,7h1v1h-1zM0,8h1v1h-1zM6,8h4v1h-4zM15,8h1v1h-1zM0,9h1v1h-1zM6,9h1v1h-1zM9,9h1v1h-1zM15,9h1v1h-1zM1,10h1v1h-1zM6,10h1v1h-1zM9,10h1v1h-1zM14,10h1v1h-1zM2,11h1v1h-1zM5,11h2v1h-2zM9,11h2v1h-2zM13,11h1v1h-1zM3,12h2v1h-2zM11,12h2v1h-2zM5,13h6v1h-6z",
  GENERIC: "M2,3h2v1h-2zM12,3h2v1h-2zM1,4h3v1h-3zM12,4h3v1h-3zM1,5h3v1h-3zM12,5h3v1h-3zM1,6h14v1h-14zM1,7h14v1h-14zM1,8h3v1h-3zM12,8h3v1h-3zM1,9h3v1h-3zM12,9h3v1h-3zM2,10h2v1h-2zM12,10h2v1h-2z",
};

export const backIcon = "M6,3h2v1h-2zM5,4h2v1h-2zM4,5h2v1h-2zM3,6h2v1h-2zM2,7h12v1h-12zM2,8h12v1h-12zM3,9h2v1h-2zM4,10h2v1h-2zM5,11h2v1h-2zM6,12h2v1h-2z";
export const chevronIcon = "M5,2h2v1h-2zM5,3h3v1h-3zM6,4h3v1h-3zM7,5h3v1h-3zM8,6h3v1h-3zM8,7h3v1h-3zM7,8h3v1h-3zM6,9h3v1h-3zM5,10h3v1h-3zM5,11h2v1h-2z";
