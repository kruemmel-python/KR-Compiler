# Warning-free build status and validation coverage

## Implemented fixes
- C++ aggregate zero initialization uses value initialization (`{}`) for the relevant types.
- Explicit float conversion for `RAND_MAX` arithmetic.
- Removed an unused renderer device local.
- Replaced shared-state `strtok` parsing with local token splitting.
- Replaced potentially truncating `strncpy` calls at the reported sites with bounded copies and explicit terminators.
- Annotated genuinely unused compatibility/placeholder parameters and currently uncalled internal helper functions with `[[maybe_unused]]` rather than suppressing warning categories.
- GCC/Clang CMake options are `-Wall -Wextra -Werror`; MSVC is `/W4 /WX` (MSVC build not yet verified).

## Verified
Local Linux x64 CMake/Ninja Release build completes without warnings or errors with GCC warnings treated as errors.

## Not yet verified
Android Clang 21.1.8 and Windows MSVC/Clang cross-platform builds must be tested independently.

## Numerical coverage
Only the existing seven GPU/CPU numerical kernel comparisons are implemented. The remaining 43 registered core kernels do not yet have independent CPU-reference execution tests. Kernel compilation is not execution validation. No 50/50 numerical coverage claim is made.

## Android Clang 21.1.8: verbleibende Warnung

Im Android-Build wurde `cc_log_sum_exp_three` als unbenutzte statische Funktion gemeldet.
Die Funktion hatte keine Aufrufstellen und wurde aus `src/CC_OpenCL.c` entfernt.
Es wurden keine Warnungsunterdrückungen hinzugefügt. Ein Linux-x64-Build mit
`-Wall -Wextra -Werror` wurde danach erfolgreich durchgeführt.
Die Bestätigung für Android ARM64 und Windows x64 erfordert Builds auf diesen Plattformen.
Die zusätzliche numerische Validierung der übrigen 43 Kernel ist weiterhin offen.
