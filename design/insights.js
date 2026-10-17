// Phnomy · insights, history, settings, and first-use states.
// Loaded after the shared editable Figma primitives.
{
  const full = 364;
  const inside = 332;
  const center = n => { n.counterAxisAlignItems = 'CENTER'; return n; };
  const between = n => { n.primaryAxisAlignItems = 'SPACE_BETWEEN'; return n; };

  function smallLabel(parent, label, value, width = 158) {
    const c = col(parent, label, width, 64, 4, 0);
    text(c, label, 12, 'muted', 'Regular', width);
    text(c, value, 26, 'text', 'Bold', width);
    return c;
  }

  function periodTabs(parent, selected = 'week') {
    const tabs = row(parent, '統計區間', full, 44, 4, 4, 'soft', 14);
    const day = center(row(tabs, '今日', 176, 36, 8, 0, selected === 'today' ? 'surface' : null, 10));
    day.primaryAxisAlignItems = 'CENTER';
    text(day, '今日', 14, selected === 'today' ? 'text' : 'muted', 'Medium');
    tag(day, 'stats_today');
    const week = center(row(tabs, '近七日', 176, 36, 8, 0, selected === 'week' ? 'surface' : null, 10));
    week.primaryAxisAlignItems = 'CENTER';
    text(week, '近七日', 14, selected === 'week' ? 'text' : 'muted', 'Medium');
    tag(week, 'stats_week');
  }

  function weekChart(parent, empty = false) {
    const c = col(parent, '七日專注圖表', full, 206, 12, 16, 'surface', 20);
    const header = center(between(row(c, '圖表標題', inside, 29, 8)));
    text(header, '近七日專注', 14, 'muted', 'Medium');
    text(header, empty ? '0 分鐘' : '6 小時 20 分', 19, 'text', 'Bold');
    const chart = row(c, '可編輯七日柱狀圖', inside, 104, 8, 0);
    chart.counterAxisAlignItems = 'MAX';
    const minutes = empty ? [0, 0, 0, 0, 0, 0, 0] : [50, 25, 75, 45, 50, 60, 75];
    const dates = ['10', '11', '12', '13', '14', '15', '16'];
    minutes.forEach((v, i) => {
      const day = col(chart, `10/${dates[i]}・${v}分鐘`, 40, 104, 4, 0);
      day.counterAxisAlignItems = 'CENTER';
      spacer(day);
      const bar = box(day, `專注 ${v} 分鐘`, 24, empty ? 3 : Math.max(8, Math.round(v / 75 * 70)), i === 6 ? 'primary' : 'soft', 6);
      if (empty) bindFill(bar, 'line');
      text(day, dates[i], 11, i === 6 ? 'primary' : 'muted', i === 6 ? 'Bold' : 'Regular', 24);
    });
    const legend = center(between(row(c, '圖表註解', inside, 17, 8)));
    text(legend, '10 月 10 日 — 16 日', 11, 'muted');
    text(legend, empty ? '從今天開始累積' : '每天，慢慢找回自己的節奏', 11, 'muted');
    return c;
  }

  function completionStrip(parent) {
    const strip = center(between(row(parent, '七日概覽', full, 72, 12, 16, 'soft', 16)));
    const a = col(strip, '完成率', 150, 40, 2);
    text(a, '完成率  86%', 14, 'primary', 'Bold');
    text(a, '12 / 14 次專注', 11, 'muted');
    const b = col(strip, '攔截次數', 150, 40, 2);
    text(b, '攔截  48 次', 14, 'primary', 'Bold');
    text(b, '把注意力帶回此刻', 11, 'muted');
  }

  function todayMetrics(parent, hasUsage = true) {
    const r = row(parent, '今日概覽', full, 92, 12);
    const focus = col(r, '今日專注時間', 176, 92, 4, 14, 'surface', 18);
    text(focus, '今日專注', 12, 'muted');
    const a = center(row(focus, '專注數值', 148, 32, 5));
    mono(a, '75', 28, 'text');
    text(a, '分鐘', 12, 'muted');
    const usage = col(r, '今日手機使用時間', 176, 92, 4, 14, 'surface', 18);
    text(usage, '今日手機使用', 12, 'muted');
    const b = center(row(usage, '使用數值', 148, 32, 5));
    mono(b, hasUsage ? '95' : '—', 28, 'text');
    text(b, hasUsage ? '分鐘' : '尚未授權', 12, 'muted');
  }

  function usageApps(parent) {
    const c = col(parent, '今日App使用時間', full, 150, 6, 16, 'surface', 20);
    const head = center(between(row(c, 'App使用標題', inside, 22, 8)));
    text(head, '今日 App 使用時間', 14, 'text', 'Medium');
    text(head, '共 95 分鐘', 11, 'muted');
    const entries = [['YouTube', '35 分', 116], ['Instagram', '25 分', 83], ['Chrome', '20 分', 66], ['LINE', '15 分', 50]];
    entries.forEach(([name, time, width]) => {
      const r = center(row(c, `使用時間・${name}`, inside, 17, 8));
      text(r, name, 11, 'muted', 'Regular', 76);
      const rail = box(r, `${name}時間軌道`, 176, 4, 'soft', 2);
      box(rail, `${name}使用比例`, width, 4, 'primary', 2);
      text(r, time, 11, 'text', 'Medium', 48);
    });
    return c;
  }

  function demoFooter(parent, withHistory = true) {
    const r = center(between(row(parent, '展示資料說明', full, 34, 8)));
    text(r, '設計展示資料 · 僅儲存於本機', 10, 'muted');
    if (withHistory) {
      const link = center(row(r, '歷史紀錄入口', 86, 32, 4));
      text(link, '歷史紀錄', 12, 'primary', 'Medium');
      icon(link, 'chevron', 16, 'primary');
      tag(link, 'history');
    }
  }

  // 11 — Statistics: all bars and labels remain independently editable.
  {
    const { body } = screen('stats', { title: '看見累積的改變', subtitle: '看見每一次把注意力帶回來的努力', nav: 'stats', back: false, row: 'insights' });
    gap(body, 10);
    periodTabs(body, 'week');
    weekChart(body);
    completionStrip(body);
    todayMetrics(body, true);
    usageApps(body);
    demoFooter(body);
  }

  function historyRecord(parent, { date, title, detail, time, status, role = 'primary' }) {
    const c = col(parent, `${date}・${title}`, full, 116, 10, 16, 'surface', 18);
    const h = center(between(row(c, '紀錄標題', inside, 28, 8)));
    text(h, title, 16, 'text', 'Medium');
    pill(h, status, role);
    const meta = center(between(row(c, '紀錄時間', inside, 18, 8)));
    text(meta, date, 12, 'muted');
    text(meta, time, 12, 'text', 'Medium');
    text(c, detail, 12, 'muted', 'Regular', inside);
    return c;
  }

  // 12 — History, with transparent interrupted and early-exit outcomes.
  {
    const { body } = screen('history', { title: '每一次專注，都算數', subtitle: '歷史紀錄', nav: 'stats', back: true, row: 'insights' });
    gap(body, 10);
    const summary = center(between(row(body, '歷史概覽', full, 50, 12, 14, 'soft', 14)));
    text(summary, '近七日', 13, 'muted');
    text(summary, '完成 12 次 · 累積 6 小時 20 分', 13, 'primary', 'Medium');
    historyRecord(body, { date: '10 月 16 日 · 14:30', title: '單次專注', detail: '有始有終，為自己留下一段安靜。', time: '25 分鐘', status: '已完成' });
    historyRecord(body, { date: '10 月 16 日 · 09:00', title: '單次專注', detail: '今天已完成 75 分鐘專注。', time: '50 分鐘', status: '已完成' });
    historyRecord(body, { date: '10 月 15 日 · 16:20', title: '提前結束', detail: '退出原因：需要處理臨時文件', time: '已專注 10 分鐘', status: '提前退出', role: 'warning' });
    historyRecord(body, { date: '10 月 12 日 · 11:10', title: '專注中斷', detail: '攔截權限已關閉，本次未計為完成。', time: '已專注 5 分鐘', status: '已中斷', role: 'warning' });
    demoFooter(body, false);
  }

  function settingLine(parent, label, value, action, opts = {}) {
    const r = center(row(parent, label, inside, opts.height || 48, 8));
    if (opts.icon) icon(r, opts.icon, 20, 'muted');
    text(r, label, 14, opts.warning ? 'warning' : 'text', 'Regular', opts.icon ? 176 : 204);
    text(r, value, 12, 'muted', 'Regular', opts.icon ? 84 : 56);
    icon(r, 'chevron', 16, 'muted');
    if (action) tag(r, action);
    return r;
  }

  function sectionHeading(parent, label) {
    text(parent, label, 12, 'muted', 'Medium', full);
  }

  // 13 — Settings: permissions, preferences and local ownership.
  {
    const { body } = screen('settings', { title: '照自己的節奏', subtitle: '設定', nav: 'settings', back: false, row: 'insights' });
    gap(body, 10);
    sectionHeading(body, '權限與服務');
    const permissions = col(body, '權限與服務', full, 170, 0, 12, 'surface', 20);
    settingLine(permissions, 'App 攔截服務', '已啟用', 'permissions', { icon: 'shield', height: 48 });
    divider(permissions, inside);
    settingLine(permissions, '使用情況存取', '已允許', 'stats_no_access', { icon: 'chart', height: 48 });
    divider(permissions, inside);
    settingLine(permissions, '勿擾模式', '跟隨每次設定', 'dnd_info', { icon: 'moon', height: 48 });
    const pomodoro = col(body, '番茄鐘預設值', full, 108, 6, 12, 'surface', 20);
    text(pomodoro, '番茄鐘預設值', 12, 'muted', 'Medium', inside);
    const a = center(between(row(pomodoro, '工作與短休息', inside, 24, 10)));
    text(a, '專注', 13, 'muted');
    text(a, '25 分鐘', 15, 'text', 'Medium');
    text(a, '短休息', 13, 'muted');
    text(a, '5 分鐘', 15, 'text', 'Medium');
    const b = center(between(row(pomodoro, '長休息與輪數', inside, 24, 10)));
    text(b, '長休息', 13, 'muted');
    text(b, '15 分鐘', 15, 'text', 'Medium');
    text(b, '長休息間隔', 13, 'muted');
    text(b, '每 4 輪', 15, 'text', 'Medium');
    tag(pomodoro, 'pomodoro_settings');
    sectionHeading(body, '偏好與資料');
    const preferences = col(body, '偏好與本機資料', full, 121, 0, 12, 'surface', 20);
    settingLine(preferences, '外觀', '淺色', 'theme', { icon: 'sun', height: 48 });
    divider(preferences, inside);
    settingLine(preferences, '清除本機資料', '', 'clear_data', { icon: 'history', warning: true, height: 48 });
    const note = center(row(body, '勿擾說明', full, 42, 8, 0));
    icon(note, 'info', 18, 'muted');
    text(note, '勿擾結束時，保留系統的來電與鬧鐘例外。\n專注進行中，無法清除本次資料。', 11, 'muted', 'Regular', 334);
    const footer = center(between(row(body, '版本與隱私', full, 18, 8)));
    text(footer, 'Phnomy 1.0', 11, 'muted');
    text(footer, '完整離線 · 無廣告 · 無追蹤', 11, 'muted');
  }

  // State — Usage access optional; concentration data remains available.
  {
    const { body } = screen('stats_no_access', { title: '看見累積的改變', subtitle: '你的專注紀錄，依然完整保留', nav: 'stats', back: false, row: 'states' });
    gap(body, 10);
    periodTabs(body, 'week');
    weekChart(body);
    todayMetrics(body, false);
    const card = col(body, '未授予使用情況權限', full, 164, 8, 16, 'soft', 20);
    const title = center(row(card, '權限提示標題', inside, 22, 8));
    icon(title, 'chart', 20, 'primary');
    text(title, '想看見手機時間的去向？', 15, 'primary', 'Medium');
    text(card, '開啟權限，即可查看各 App 的使用時間。\n你仍可繼續專注，無須授予這項權限。', 12, 'muted', 'Regular', inside);
    const enable = button(card, '前往開啟使用情況存取', 'secondary', inside);
    tag(enable, 'usage_access');
    completionStrip(body);
    demoFooter(body);
  }

  // State — First visit, before any sessions have been recorded.
  {
    const { body } = screen('stats_empty', { title: '你的專注，從這裡開始', subtitle: '一點點安靜，也能帶來改變', nav: 'stats', back: false, row: 'states' });
    gap(body, 10);
    periodTabs(body, 'week');
    weekChart(body, true);
    const c = col(body, '首次統計空狀態', full, 292, 16, 24, 'surface', 24);
    c.counterAxisAlignItems = 'CENTER';
    const illustration = center(row(c, '可編輯葉片標記', 64, 64, 0, 0, 'soft', 32));
    illustration.primaryAxisAlignItems = 'CENTER';
    icon(illustration, 'leaf', 32, 'primary');
    text(c, '先留 25 分鐘給自己', 22, 'text', 'Bold', 316);
    text(c, '完成第一段專注後，\n這裡會記下你的每一小步。', 14, 'muted', 'Regular', 316);
    const start = button(c, '開始第一次專注', 'primary', 316);
    tag(start, 'home');
    const note = center(row(body, '首次資料提示', full, 40, 8));
    icon(note, 'shield', 18, 'muted');
    text(note, '你的專注紀錄只留在這部手機。', 12, 'muted');
  }
}
