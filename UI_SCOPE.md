# MindTrack Android app (v2)

## What it does
A private, on-device check-in. 41 one-tap questions in three parts (about you, 22 phone-dependence items, 13 anxiety items) feed a random-forest model exported from `MindTrack_Project/notebooks/Pipeline.ipynb`. The model estimates whether the answers resemble students with Beck Depression Inventory totals of 11 or more. Output: **Low** or **Elevated** indicator, with a phone-dependence band, wellbeing tips and Kenyan support lines. Not a diagnosis.

## Screens
Welcome → Name → Consent → Home (tabs: Home, History, About, Profile) → Check-in wizard (section intros, auto-advance, progress per part, exit confirmation) → Result (gauge, explanation, support, tips). History shows a trend chart and past results. Profile edits the name and deletes all data.

## Code map (`app/src/main/java/com/example/mindtrack_app/`)
| File | Role |
|---|---|
| `MainActivity.kt` | App shell, tab bar, navigation, back handling |
| `MindTrackViewModel.kt` | State, check-in flow, model loading, saving results |
| `model/MindTrackModel.kt` | On-device random forest (reads `assets/mindtrack_model.json`) |
| `data/Questions.kt` | All 41 questions, answer codes, model-input builder |
| `data/MindTrackRepository.kt` | Storage interface + `LocalRepository` (SharedPreferences). Firebase goes here later. |
| `ui/*.kt` | Screens, components, icons; `ui/theme/` colours and type |

Test: `app/src/test/.../ModelParityTest.kt` checks Kotlin predictions equal the Python notebook's.

## Updating the model
Re-run the notebook, then copy `MindTrack_Project/app/mindtrack_model.json` to `app/src/main/assets/` and `test_vectors.json` to `app/src/test/resources/`, and run ModelParityTest.

## Firebase (next)
Implement `MindTrackRepository` with Firebase Auth + Firestore (`users/{uid}/checkIns`), and swap it in `MindTrackViewModel`. Keep local-only as the default until the user opts in.

The old UI is backed up in `_old_ui_backup/`.
