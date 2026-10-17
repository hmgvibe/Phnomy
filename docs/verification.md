# Phnomy 1.0.0 驗證報告

日期：2026-10-17。套件：`com.ray.phnomy`，versionCode 1，Android 12 / API 31 以上，target / compile SDK 36。

## 建置與自動檢查

JDK 21、Gradle 8.13、AGP 8.13.2、Kotlin 2.2.21；執行 `assembleDebug testDebugUnitTest lintRelease assembleRelease assembleDebugAndroidTest` 全部成功。

| 檢查 | 結果 |
|---|---|
| SessionEngine | 14 / 14 通過 |
| FocusController | 26 / 26 通過 |
| Room / DataStore | 13 / 13 通過 |
| JSON / 復原驗證 | 11 / 11 通過 |
| 平台 Provider（Robolectric API 31、36） | 18 / 18 通過 |
| 白名單與分割視窗決策 | 7 / 7 通過 |
| 原始 Figma SVG 品牌色像素 | 1 / 1 通過 |
| 單元與平台測試合計 | **90 通過，0 失敗，0 跳過** |
| Compose UI：Android 12 模擬器 | **4 / 4 通過** |
| Compose UI：Android 16 模擬器 | **4 / 4 通過** |
| Release lint | **0 errors，29 warnings** |
| APK 簽章 | `apksigner verify` 通過，RSA 3072、APK v2 |

Lint 警告主要為固定版本有更新、KTX 寫法、kapt/KSP 與 Compose 參數風格等建議；未加入 lint baseline 或忽略錯誤。機器可讀摘要見 [verification-results.json](verification-results.json)，原始測試／lint 與 instrumentation 輸出在 [evidence/](evidence/)。

計時測試涵蓋工作／休息邊界、最終長休息、單調時鐘、重啟 checkpoint、自然完成優先、60 秒前禁止退出、取消／恢復退出等待、不可變白名單、重複 finalize、損壞資料隔離、取消中的交易一致性，以及延遲建立 UI 後仍可收到結果。平台測試驗證必要系統套件、IME 不替底下 App 解禁、前景安全表面與自己的 DND 規則／來電例外。

UI 測試涵蓋空白原因不可下一步、等待剩餘 1 毫秒仍不可確認、必要 App 不可取消而一般 App 可取消，以及圓環剩餘比例語義會更新。

## 模擬器實際操作

使用本案建立的 Pixel 6 / 1080 × 2400 模擬器，API 31 與 API 36。全程使用指定的 emulator serial；沒有連線或操作實體手機。以下截圖為執行中的原生 App，沒有寫入設計稿的示例歷史。

| 情境 | 實測結果與畫面 |
|---|---|
| 首頁與字型 | [暖白首頁](screenshots/api31-home.png)。依截圖修正 variable font 字重，Noto Sans TC 明確使用 400／500／700；原始 SVG 與 Roboto Mono 保留。 |
| 單次專注 | 設定 1 分鐘並開始；[00:59](screenshots/api31-focus.png) 到 [00:18](screenshots/api31-progress.png)，數字與圓環持續更新。 |
| 非白名單 | 開啟 Chrome 後顯示[攔截畫面](screenshots/api31-blocked.png)。在同一段專注透過 DIAL intent 成功開啟系統 Phone Activity。 |
| 自然完成 | 經過真實 60 秒後顯示[完成結果](screenshots/api31-completed.png)：本次 1 分鐘、攔截紀錄保留。 |
| 休息白名單 | 設定 1 分鐘工作／1 分鐘短休息、2 輪；自動進入[休息畫面](screenshots/api31-break.png)，休息時 Chrome 仍顯示[攔截](screenshots/api31-break-blocked.png)。 |
| 權限中斷 | 在上述休息期間關閉服務，立即記為[中斷](screenshots/api31-interrupted.png)，本次有效專注 1 分鐘、完成 1 輪，沒有將休息時間加進專注。 |
| 發行版實際安裝 | 已在 API 36 安裝經 R8 縮減、獨立簽署的 release APK；[首頁](screenshots/api36-release-home.png)、[深色專注](screenshots/api36-dark-focus.png)可運行。 |
| 未授權使用時間 | [深色統計](screenshots/api36-dark-statistics.png)顯示「— 未授權」與真實空狀態，沒有展示用統計。 |
| 退出原因與等待 | [原因必填](screenshots/api36-exit-reason.png) → [60 秒等待](screenshots/api36-exit-wait.png)。等待約 38 秒時嘗試點確認，仍停在[剩餘 22 秒](screenshots/api36-exit-still-waiting.png)，沒有退出。 |
| 到期確認 | 滿 60 秒後出現[可確認狀態](screenshots/api36-exit-ready.png)，點擊後正確記為[提前退出](screenshots/api36-exited.png)，保存輸入原因。原工作計時在等待期間持續。 |
| 強制停止恢復 | API 36 發行版另開新專注後強制停止，再次開啟顯示[中斷](screenshots/api36-force-stop-interrupted.png)，未誤記完成。 |

執行日誌沒有 `AndroidRuntime` 崩潰。UIAutomator 的預設 shell dump 會短暫壓制其他無障礙服務，因此實測 active session 期間使用 ADB input、screencap 與系統 dumpsys；沒有在 active session 以 shell UIAutomator 製造服務中斷。Compose gate tests在沒有 active session 的情況執行。

## 發行檔案

- `artifacts/Phnomy-v1.0.0.apk`：**10,859,296 bytes**，非 debuggable。
- SHA-256：`89e131d58601d7a6982247b4bfec5a143b51ca3d332eedcf5f30a0f20e31ba90`
- 簽章憑證 SHA-256：`141c522f70f69bac84fd95f0ca00a805cb7e1b03d9fd1ce5054b13e973372700`
- 原始碼 ZIP 與各檔 SHA-256 由 `scripts/package.ps1` 產生，見 `artifacts/SHA256SUMS.txt`。

最終 APK 的 manifest 已用 `aapt2` 檢查，沒有 INTERNET、QUERY_ALL_PACKAGES、裝置管理或 root 權限。無障礙服務的系統綁定權限僅放在 service 宣告，使用前必須主動授權。本機資料也排除雲端備份及裝置轉移。原始碼封裝採明確白名單，排除簽章金鑰、密碼、本機 SDK 路徑、工具與建置快取。

## 驗證界線

本次驗證包含單元測試、Robolectric、Android 12 / 16 模擬器及上述真實計時流程。沒有實體裝置、廠牌省電管理、長時間 Doze、實機重開機、完整分割畫面手勢／浮動視窗矩陣或實際來電／鬧鐘音訊驗收。重啟、分割視窗決策與 DND 政策有自動測試，這些不能代替上述實機驗收。

一般安裝無法阻止使用者關閉服務、強制停止或解除安裝；Phnomy 會在可檢測的保護失效／重新開啟時記錄中斷。系統 App 使用統計來自每日 bucket，可能含分割畫面重疊；不將其宣稱為精確的螢幕開啟時間。
