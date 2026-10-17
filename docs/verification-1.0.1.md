# Phnomy 1.0.1 驗證報告

日期：2026-10-17。Android 12（API 31）以上，套件 `com.ray.phnomy`，versionCode 2。

## 變更

- 自然完成顯示「專注時間到」通知；番茄鐘工作與休息結束也會發送階段提醒。點通知返回 App。
- 新的「計時結束提醒」頻道採 HIGH 重要性、系統預設提示音與振動。保留使用者已調整的舊頻道偏好，不覆寫系統勿擾、音量或通知設定。
- Android 13 以上提供通知授權入口；曾拒絕且系統不再顯示授權視窗時，導向 App 通知設定。提醒為選用，可略過並開始。
- Noto Sans TC 一般、強調、標題字重各由 400／500／700 微增至 450／550／750；Roboto Mono 由 400 微增至 450。Compose 與原生攔截畫面一致，尺寸和間距未變。

## 自動化驗證

完整執行 `assembleDebug testDebugUnitTest lintRelease assembleRelease assembleDebugAndroidTest --continue`，建置成功。

| 項目 | 結果 |
|---|---|
| 單元與平台測試 | 120 項，0 失敗、0 跳過 |
| 提醒回歸測試 | 上述包含 30 項，分別覆蓋 API 31 與 36 |
| API 31 Compose UI 測試 | 5 項通過，包含提醒授權、略過及授權後開始的流程 |
| Release lint | 0 錯誤，34 警告；未使用 lint baseline |

提醒回歸涵蓋自然完成、工作／短休息／長休息切換、聲音與振動頻道設定、舊頻道偏好、缺少授權、首次開始、程序重建、防止重複通知，以及提前退出與中斷不誤發完成提醒。

## 模擬器驗證

- Pixel 6 / API 36：同簽章由 1.0.0 覆蓋安裝 1.0.1 成功，既有「今日已專注 4 分鐘」與主題保留。
- API 36：實際撤銷通知權限，從檢查頁點「計時結束提醒」可開啟 Android 通知授權視窗並授權。
- API 36：執行 1 分鐘單次專注並切至背景，核對自然完成的系統通知與點擊返回。
- 檢視實際專注畫面的新字重，未發現裁切或對齊異常。

證據：[`evidence-1.0.1/`](evidence-1.0.1/)、[新字重專注畫面](screenshots/v101-focus-typography.png)、[到時通知](screenshots/v101-time-up-notification.png)、[結束結果](screenshots/v101-completed.png)。機器可讀結果見 [`verification-results-1.0.1.json`](verification-results-1.0.1.json)。

## 驗證邊界

本版未操作實體手機；模擬器使用無音訊模式，提示音與振動已驗證 Android 頻道設定，未驗證實體揚聲器／振動馬達。實際提醒仍受使用者通知授權、頻道設定、音量與系統勿擾影響。不同廠牌省電與通知呈現應以實機驗收。

1.0.0 的完整流程驗證保留於 [`verification.md`](verification.md)，本文件記錄本次更新範圍。
