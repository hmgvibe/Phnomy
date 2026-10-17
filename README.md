# Phnomy

以暖白與森林綠設計的 Android 專注 App。使用 Kotlin、Jetpack Compose、Room 與 DataStore，Android 12（API 31）以上可用。所有資料留在本機，App 沒有網路權限、帳號、廣告或分析追蹤。

[Figma 設計與可點擊原型](https://www.figma.com/design/alcAGVEVKA4z9srUYZVPKj) · [設計總覽](design/Phnomy-UI-overview.png)

目前版本 **1.0.1**。可直接覆蓋安裝於 1.0.0，保留本機設定與歷史。更新內容：計時結束明確顯示提醒、番茄鐘工作／休息切換提醒，以及全局字重微增；[本版驗證報告](docs/verification-1.0.1.md)。

## 功能

- 單次專注：25、50 分鐘或自訂 1–180 分鐘。
- 番茄鐘：工作 1–180 分鐘、短休息 1–60 分鐘、長休息 1–90 分鐘、1–12 輪；每 4 輪長休息。第 4、8、12 輪若為最後一輪，完成長休息才結束。預設 4 × 25 分鐘有 3 段 5 分鐘短休息與最後 15 分鐘長休息，共 130 分鐘，其中有效專注 100 分鐘。
- 專注與所有休息期間，持續覆蓋非白名單 App。開始時鎖定本次計畫；途中不能暫停、縮短、增加白名單。
- 電話、緊急撥號、系統設定、權限管理、主畫面、輸入法等必要功能保持可用。這些功能所屬的套件也會保留使用權。
- 提前退出必須填原因並等待 60 秒；可隨時取消，原計時照常進行。若計畫先自然完成，結果以完成為準。
- 進度通知、計時結束通知／提示音／振動、選用勿擾、淺色／深色／系統主題。番茄鐘工作與休息結束也會提醒，點通知可返回 App；實際聲音與呈現依系統音量、通知及勿擾設定。
- 本機歷史、完成率、攔截紀錄；手機 App 使用合計來自選用的 Android 使用情況權限。未授權顯示「—」。系統每日彙總可能包含分割畫面重疊時間，並非精確的螢幕開啟時間。

## 安裝與第一次使用

1. 安裝發行 APK（Android 12 以上），開啟 Phnomy。
2. 選時間與允許的 App，按開始，在檢查頁閱讀無障礙用途並開啟服務。
3. 允許「鬧鐘與提醒」特殊存取，用於睡眠／背景中的階段切換。返回 App 後，等服務連接完成再開始。
4. 在檢查頁選「開啟計時結束提醒」並允許通知；先前拒絕時可從同一入口前往系統通知設定。通知仍可略過，但未允許時只能在 App 內看完成結果。使用情況存取與勿擾亦為選用；若開啟首頁的勿擾選項，檢查頁會引導取得對應存取。

部分 Android 13 以上裝置會對側載 App 顯示「受限制的設定」。可在系統的 Phnomy App 資訊右上角選單允許受限制設定，再由無障礙設定啟用。不同品牌的系統名稱可能不同。

## 保護與資料說明

這是一般安裝的 App，使用使用者主動授權的無障礙服務，並非裝置管理鎖。只讀取視窗所屬套件資訊來判斷 App，不讀取畫面文字、聊天內容、輸入內容、不遍歷視窗子節點、不擷取螢幕。分割畫面也檢查可見應用套件。使用者仍能撤銷權限、強制停止或解除安裝。

計時採單調時鐘 `elapsedRealtime`，包含螢幕關閉／裝置睡眠時間，不受手動更改日期影響。背景恢復時依原始時間推算階段；重開機、必要權限失效、保護服務中斷或無法顯示覆蓋畫面會記為「中斷」，只認列最後保存的專注時間檢查點（最長約 15 秒間隔）。不會把中斷偽裝成完成。

勿擾只建立並控制 Phnomy 自己的自動規則，結束時停用此規則，保留系統來電、重複來電與鬧鐘例外，不覆寫使用者的其他規則。OEM 省電、浮動視窗、廠商特殊桌面與實體裝置重開機行為仍應做實機驗收。測試範圍與結果見 [驗證報告](docs/verification.md)。

清除本機資料會刪除專注、攔截與復原紀錄，保留白名單與偏好。專注期間不允許清除。系統備份已停用；解除安裝會刪除本機資料。

## 建置

工具：JDK 21、Gradle 8.13、Android SDK 36，Java bytecode 17。Windows 可執行：

```powershell
.\scripts\bootstrap-tools.ps1          # 專案內下載、校驗官方工具
.\scripts\build.ps1                   # debug APK、單元測試、lint
.\scripts\build.ps1 -Tasks assembleDebugAndroidTest
```

或設定 `JAVA_HOME`、`ANDROID_HOME` 後使用 `gradlew.bat --no-daemon assembleDebug testDebugUnitTest lintDebug`。第一次需連線下載依賴；執行中的 App 本身不使用網路。

發行版：

```powershell
.\scripts\init-signing.ps1             # 僅首次，產生獨立私有簽章
.\scripts\build.ps1 -Tasks assembleRelease
```

`signing.properties` 與 `.tools/signing/phnomy-release.jks` 必須自行妥善備份，之後同套件更新須使用同一份金鑰。這兩者不納入原始碼 ZIP 或 Git。沒有本機簽章設定時，release 產出為未簽署版本。

## 原始碼

- `core/`：純 Kotlin 計時狀態機、退出規則、結果與狀態控制器。
- `data/`：Room 交易、JSON 驗證／損壞資料隔離與 DataStore 偏好。
- `platform/`：無障礙覆蓋、必要 App、準確鬧鐘、使用時間、通知與應用自有勿擾規則。
- `ui/`：Figma 對應 Compose 畫面、原始 SVG 圖示、主題與 ViewModel。
- `app/src/test/`、`app/src/androidTest/`：邏輯、資料層與重要 UI gate 測試。

字型採 Noto Sans TC、Roboto Mono（SIL OFL，見 `licenses/`）。原始 Figma SVG 放在 `app/src/main/assets/figma` 並附來源對照。設計稿的示例數據不會寫入正式資料庫。
