// Supplemental, independently editable prototype sheets.
// Execute immediately after helpers.js in the same Figma evaluation scope.
{
  theme = 'Light';
  const wrapper = figma.getNodeById(STATE.wrapper);
  if (!wrapper) throw new Error('Missing screen wrapper ' + STATE.wrapper);

  const section = col(wrapper, '07 / 補充操作面板', 2320, null, 24);
  text(section, '07 / 補充操作面板', 24, 'text', 'Bold', 2320);
  text(section, '自訂時間、番茄鐘、必要 App、勿擾、外觀、清除紀錄與系統授權轉場', 14, 'muted', 'Regular', 2320);
  const firstRow = row(section, 'Sheets / 01–04', 2320, null, 32);
  const secondRow = row(section, 'Sheets / 05–07', 2320, null, 32);
  firstRow.counterAxisAlignItems = 'MIN';
  secondRow.counterAxisAlignItems = 'MIN';

  function sheet(key, title, description, parent) {
    const panel = col(parent, key + ' / ' + title, 364, null, 16, 24, 'surface', 24);
    panel.clipsContent = false;
    screens[key] = panel;
    const titleNode = text(panel, title, 20, 'text', 'Bold', 316);
    titleNode.lineHeight = { unit: 'PIXELS', value: 24 };
    if (description) text(panel, description, 14, 'muted', 'Regular', 316);
    return panel;
  }

  function sheetButton(parent, label, kind, action) {
    const node = button(parent, label, kind, 316);
    if (node.height < 56) node.resize(316, 56);
    if (action) tag(node, action);
    return node;
  }

  function note(parent, title, description, warning = false) {
    const card = col(parent, title, 316, null, 8, 16, warning ? 'warningSoft' : 'soft', 16);
    text(card, title, 14, warning ? 'warning' : 'primary', 'Medium', 284);
    if (description) text(card, description, 14, 'muted', 'Regular', 284);
    return card;
  }

  function option(parent, label, description, selected, action, iconName = null) {
    const node = row(parent, label, 316, 72, 12, 12, selected ? 'soft' : 'bg', 16);
    if (iconName) icon(node, iconName, 24, selected ? 'primary' : 'muted');
    const labelWidth = iconName ? 224 : 268;
    const copy = col(node, label + ' label', labelWidth, null, 2);
    text(copy, label, 14, selected ? 'primary' : 'text', 'Medium', labelWidth);
    if (description) text(copy, description, 12, 'muted', 'Regular', labelWidth);
    if (selected) icon(node, 'check', 20, 'primary');
    if (action) tag(node, action);
    return node;
  }

  // 01 / Custom duration — the prototype demonstrates the 30-minute choice.
  {
    const panel = sheet('custom', '留多少時間給自己？', '選一段適合現在的時間。', firstRow);
    const duration = row(panel, 'Custom duration / 30 minutes', 316, 112, 8, 0, 'soft', 20);
    duration.primaryAxisAlignItems = 'CENTER';
    mono(duration, '30', 64, 'primary');
    text(duration, '分鐘', 14, 'muted');
    const presets = row(panel, 'Duration presets', 316, 56, 12);
    for (const minutes of [15, 30, 60]) {
      const selected = minutes === 30;
      const choice = row(presets, 'Preset / ' + minutes + ' minutes', (316 - 24) / 3, 56, 0, 0, selected ? 'soft' : 'bg', 16);
      choice.primaryAxisAlignItems = 'CENTER';
      text(choice, minutes + ' 分鐘', 14, selected ? 'primary' : 'muted', 'Medium');
    }
    text(panel, '開始後，這段時間就留給眼前的事。', 14, 'muted', 'Regular', 316);
    sheetButton(panel, '套用 30 分鐘', 'primary', 'sheet_custom_apply');
    sheetButton(panel, '取消', 'ghost', 'sheet_custom_cancel');
  }

  // 02 / Pomodoro defaults — no limit is lifted during either break.
  {
    const panel = sheet('pomodoro_settings', '調整你的專注節奏', '專注、休息，慢慢找到自己的步調。', firstRow);
    const timings = col(panel, 'Pomodoro timing values', 316, null, 0, 16, 'bg', 16);
    for (const [label, value] of [['專注', '25 分鐘'], ['短休息', '5 分鐘'], ['長休息', '15 分鐘'], ['長休息間隔', '每 4 輪']]) {
      const setting = row(timings, label, 284, 56, 8);
      setting.primaryAxisAlignItems = 'SPACE_BETWEEN';
      text(setting, label, 14, 'muted');
      text(setting, value, 14, 'text', 'Medium');
      if (label !== '長休息間隔') divider(timings, 284);
    }
    note(panel, '休息也讓手機安靜一下', '專注與休息期間都維持白名單限制；休息結束後，自動開始下一輪。');
    sheetButton(panel, '儲存設定', 'primary', 'sheet_pomodoro_save');
    sheetButton(panel, '取消', 'ghost', 'sheet_pomodoro_cancel');
  }

  // 03 / These are editable shortcut representations, not live OS launches.
  {
    const panel = sheet('allowed_apps', '必要的 App，都在這裡', '保持必要聯繫，然後回到自己的節奏。', firstRow);
    const apps = row(panel, 'Allowed app shortcuts', 316, 112, 12, 0, 'bg', 16);
    apps.primaryAxisAlignItems = 'CENTER';
    for (const name of ['電話', 'LINE', 'Google 地圖']) {
      const shortcut = col(apps, 'Shortcut / ' + name, 88, 88, 8);
      shortcut.counterAxisAlignItems = 'CENTER';
      appIcon(shortcut, name, 40);
      text(shortcut, name, 12, 'muted');
    }
    note(panel, '本次白名單持續生效', '專注與休息期間，都可以使用這 3 個 App。系統設定與緊急撥號也會保留。');
    sheetButton(panel, '返回專注', 'primary', 'sheet_allowed_return');
  }

  // 04 / DND wording preserves the previously agreed system exceptions.
  {
    const panel = sheet('dnd_info', '讓通知安靜一下', '啟用勿擾，降低這段時間的通知干擾。', firstRow);
    const symbol = row(panel, 'Quiet mode symbol', 316, 88, 0, 0, 'soft', 20);
    symbol.primaryAxisAlignItems = 'CENTER';
    icon(symbol, 'moon', 40, 'primary');
    note(panel, '必要的聲音仍會保留', '保留系統已設定的來電與鬧鐘例外。你可在系統勿擾設定中查看。');
    text(panel, '專注結束時，只停用 Phnomy 建立的勿擾規則，保留其他系統設定。', 14, 'muted', 'Regular', 316);
    sheetButton(panel, '知道了', 'primary', 'sheet_dnd_close');
  }

  // 05 / A real dark home screen is used as the selectable dark preview.
  {
    const panel = sheet('theme', '選一種舒服的外觀', '讓畫面配合你，也配合此刻的光線。', secondRow);
    option(panel, '跟隨系統', '隨手機外觀切換', true, 'sheet_theme_system', 'settings');
    option(panel, '淺色', '暖紙白，安靜清楚', false, 'sheet_theme_light', 'sun');
    option(panel, '深色', '深森林綠，低亮度陪伴', false, 'sheet_theme_dark', 'moon');
    sheetButton(panel, '關閉', 'ghost', 'sheet_theme_close');
  }

  // 06 / Explicit destructive confirmation; cancellation has a separate action.
  {
    const panel = sheet('clear_data', '清除這部手機的紀錄？', '這個操作無法復原。', secondRow);
    note(panel, '專注歷史將永久刪除', '清除 Phnomy 儲存的專注紀錄、統計與退出原因。請確認這些紀錄已不再需要。', true);
    text(panel, '清除後，統計頁將回到尚無紀錄的狀態。', 14, 'muted', 'Regular', 316);
    sheetButton(panel, '取消，保留紀錄', 'secondary', 'sheet_clear_cancel');
    sheetButton(panel, '確認清除紀錄', 'warning', 'sheet_clear_confirm');
  }

  // 07 / Explain the system handoff without fabricating an Android OS screen.
  {
    const panel = sheet('system_permission', '接下來，前往手機設定', '這裡先展示授權後的專注流程。', secondRow);
    note(panel, '此原型不會變更手機權限', '手機版將引導開啟無障礙服務、準確鬧鐘，以及你選用功能所需的權限。');
    text(panel, '無障礙服務只辨識正在使用的 App，以執行白名單，不取得聊天內容或輸入文字。', 14, 'muted', 'Regular', 316);
    text(panel, '使用情況存取與勿擾為選用；未授予使用情況存取，仍可使用專注功能。', 14, 'muted', 'Regular', 316);
    sheetButton(panel, '展示授權完成', 'primary', 'sheet_permission_ready');
    sheetButton(panel, '返回權限說明', 'ghost', 'sheet_permission_back');
  }
}
