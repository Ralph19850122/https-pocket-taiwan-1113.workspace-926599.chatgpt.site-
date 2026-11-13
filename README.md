# 一起收 TogetherGo

原生 Android APP，讓朋友共同收藏台灣吃喝玩樂。Java 17、Android SDK 35、Firebase Authentication / Firestore；最低 Android 8.0。介面使用繁體中文、奶油白與森林綠，首頁以收藏列表為主。

## 試用

交付的 `dist/一起收-debug.apk` 為可側載的測試版。傳到 Android 手機並開啟，允許該來源安裝應用程式，安裝後選「先體驗看看」。這不是 Google Play 正式發行版。

本機體驗提供五筆明確標示的虛構收藏，包含四種分類與待補資料。新增、編輯、搜尋、心得、去過／想再去可在本機使用，關閉重開仍保留。體驗資料與登入後的雲端資料隔離，不會自行上傳。

## 已實作

| 需求 | 行為 |
| --- | --- |
| Google 登入 | Credential Manager + Firebase Auth；需要下方專案設定 |
| 分享入口 | 接收 Android `ACTION_SEND` 的文字、連結與圖片，以及多張圖片分享 |
| 快速儲存 | 可只存連結、文字或截圖，列入「待補資料」 |
| 輔助辨識 | 從分享文字找候選名稱，嘗試對應台灣縣市、鄉鎮市區與地址；需人工確認 |
| 多地點貼文 | 可整篇收藏；「儲存並繼續拆分」保留來源與文字，逐筆確認另存 |
| 共用清單 | 建立、切換、分享邀請代碼、申請加入；管理員核准後才可讀寫 |
| 地點編輯／刪除 | 成員皆可編輯；新增者、管理員可刪除；Firestore 規則亦強制檢查 |
| 個人紀錄 | 各自編輯署名備註、心得、去過、想再去；其他成員與管理員不能改寫 |
| 重複收藏 | 比對同來源連結，或名稱＋縣市＋鄉鎮市區；可加入原收藏的來源與個人備註 |
| 分類 | 美食、景點、住宿、活動，可複選 |
| 篩選／搜尋 | 22 縣市及 368 鄉鎮市區；可搭配類型，搜尋名稱、文字、地址與所有成員備註 |
| 地圖 | 使用 Google Maps 搜尋網址開啟位置，使用者可接續導航 |
| 更新 | App 前景以 Firestore listener 更新；離開時移除 listener；沒有推播服務 |
| 編輯衝突 | 交易檢查更新版本，避免靜默覆蓋別人的較新編輯；衝突或無網路時提示重開確認 |

## 啟用真正 Google 登入與共用同步

目前沒有你的 Firebase 專案設定，因此交付 APK 預設可用本機體驗，Google 按鈕會明確說明尚未設定。**沒有假登入，也沒有假裝已同步。**

1. 在 Firebase Console 建立你自己的專案，新增 Android 應用程式，套件名稱使用 `tw.together.go`。
2. 執行 `./gradlew signingReport`，將實際使用的 debug / release SHA-1 與 SHA-256 登記到 Firebase。
3. 在 Authentication 啟用 **Google** 提供者，設定支援信箱；確認 Google Cloud 的 OAuth 同意畫面／測試使用者設定符合你的發佈階段。
4. 下載更新後的 `google-services.json` 放進 `app/google-services.json`。Google Services plugin 會產生 `default_web_client_id`；這必須是 Web OAuth client ID，不能填 Android client ID。
5. 在 Firebase 建立 **Cloud Firestore Standard edition** 資料庫。不要使用開放所有讀寫的測試規則。
6. 安裝 Firebase CLI，執行 `firebase login`，再執行 `firebase deploy --only firestore --project YOUR_PROJECT_ID` 部署本專案的規則。此步驟會更新該專案的 Firestore 規則，應選新建／專用專案。
7. 重新建置並安裝 APK。登入 Google → 清單 → 建立清單 → 分享代碼。朋友登入後輸入代碼，管理員在「查看加入申請」核准。

請勿把 OAuth client secret、服務帳號私鑰或簽章私鑰放進 APP。此專案不需要服務帳號金鑰。Google 設定檔與 keystore 已列入 `.gitignore`。

## 建置

用 Android Studio 開啟本資料夾，安裝 SDK Platform 35、JDK 17，等待 Gradle 同步後執行 `app`。或在 PowerShell 中：

```powershell
# JAVA_HOME 指向 JDK 17；Android SDK 可由 Android Studio 管理
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
```

輸出為 `app/build/outputs/apk/debug/app-debug.apk`。`local.properties` 指定你自己的 `sdk.dir`；不要複製這台電腦的路徑設定。已附標準 Gradle 8.9 wrapper，AGP 8.7.3。

正式發佈前請設定自己的 release signing、檢查當時 Google Play target SDK 政策，並完成實機驗收、資料安全表單及隱私權政策。此版本使用 debug 簽章，方便安裝測試。

## 測試

```powershell
.\gradlew.bat testDebugUnitTest lintDebug
# Firestore 權限測試，需 Node.js、套件管理器與 JDK 17
npm install
npm run test:rules
```

單元測試涵蓋名稱／網址去重、不同分店、網址安全、分享文字候選、地區與心得搜尋。Robolectric 測試涵蓋首頁／待補、分享帶入、只存連結、個人紀錄持久化與刪除權限。Firestore Emulator 測試驗證訪問隔離、成員編輯、刪除範圍、心得不可冒改、加入申請與管理員核准。手動驗收項目見 `docs/ACCEPTANCE.md`；交付的實際檢查結果見 `dist/驗證結果.md`。

## 資料結構

```text
lists/{listId}                         name, admin, members[], createdAt
  places/{placeId}                     共用地點資料、來源、creator、pending、updatedAt
  records/{placeId}_{uid}              個人署名文字、visited、again
  requests/{uid}                       申請者、顯示名稱、申請時間
```

成員清單存於 `members`，查詢自己的清單使用 `array-contains`。最多 100 位成員。個人紀錄以 uid 與地點 ID 綁定，伺服器驗證寫入者本人。刪除地點後，其個人紀錄不再顯示／計入統計，但後端子紀錄仍保留；正式營運需補後端清理與帳號資料刪除流程。

本機體驗使用 Android 私有 SharedPreferences；截圖複製到 APP 私有目錄，最多五張、每張 12 MB。雲端模式亦僅在此裝置保留截圖，其他成員看不到；備份預設關閉。

## 已知限制與後續工作

- **尚未實測 FB 真實內容辨識**：APP 不爬取 FB，也不繞過登入限制。只有分享的連結時無法讀取貼文內容；先存連結是可用的基本流程。文字候選不代表查證過的店家。
- **圖片僅本機儲存**：可以補截圖，但目前沒有 OCR、圖片上傳或跨裝置圖片同步。尚不能從截圖自動認店。
- **Google／跨帳號雲端流程需設定後實機驗收**：本機權限測試不等同 Firebase 真實 OAuth 或 FB 分享實機測試。
- 地點合併使用名稱／地區與來源網址啟發式比對，沒有 Google Places ID；相同店名、分店或多店家貼文仍需人工確認。Maps 是查詢連結，並非已驗證座標。
- 雲端地點儲存採交易，必須連網；斷網時不要關閉尚未儲存的編輯視窗。Firestore 讀取快取不等同完整離線寫入佇列。
- 表單尚未提供完整的程序遭終止後草稿復原；來不及填寫時請先勾「待補資料」儲存。尚無清單移交、退出、成員移除與正式帳號刪除介面。
- 個人備註與心得為一份本人可編輯的文字紀錄，其他清單成員可閱讀；不是私密日記，也不是多篇評論串。
- 本機截圖目前未提供未引用檔案回收，解除安裝會清除本機資料。

## 官方參考

- [Google 登入與 Firebase Authentication](https://firebase.google.com/docs/auth/android/google-signin)
- [Credential Manager 的 Google 登入](https://developer.android.com/identity/sign-in/credential-manager-siwg)
- [AGP 8.7 與 SDK／JDK 相容性](https://developer.android.com/build/releases/agp-8-7-0-release-notes)
- [Firestore 安全規則](https://firebase.google.com/docs/rules)
