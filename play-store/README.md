# Play Store Assets

Everything needed to submit Pocket Assistant to Google Play, organized in the
[fastlane supply](https://docs.fastlane.tools/actions/supply/) directory
layout so the same tree can be uploaded via the Play Console UI or automated
later.

## Layout

```
play-store/
├── README.md                       (this file)
├── listing-checklist.md            pre-submission checklist
├── content-rating.md               answers for the IARC questionnaire
├── data-safety.md                  answers for the Data safety form
├── metadata/
│   └── android/
│       └── en-US/
│           ├── title.txt                 (max 30 chars)
│           ├── short_description.txt     (max 80 chars)
│           ├── full_description.txt      (max 4000 chars)
│           ├── video.txt                 YouTube URL (fill after upload)
│           ├── changelogs/
│           │   └── 1.txt                 "What's new" for versionCode 1
│           └── images/
│               ├── icon/
│               │   └── icon.png                   512x512 high-res icon
│               ├── featureGraphic/
│               │   └── featureGraphic.png         1024x500 feature graphic
│               ├── phoneScreenshots/
│               │   ├── 01_home.png                1080x1920 (9:16)
│               │   ├── 02_detail.png
│               │   └── ...
│               └── promoVideo/
│                   └── promo.mp4                  30s MP4, upload to YouTube
└── release/
    └── README.md                  how to reproduce the signed AAB
```

## Upload order in the Play Console

1. **Create app** (one-time): package name `com.charles.pocketassistant`,
   language en-US, app or game → App, free, declare ads status.
2. **Main store listing**: paste fields from `metadata/android/en-US/`, upload
   icon + feature graphic + phone screenshots.
3. **Data safety**: answer using `data-safety.md`.
4. **Content rating**: answer using `content-rating.md`.
5. **Privacy policy**: point to `https://chartmann1590.github.io/Pocket-Assistant/privacy.html`.
6. **Production release**: upload the signed `.aab` produced by
   `./gradlew bundleRelease` (see `release/README.md`).
