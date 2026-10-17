// Supplemental prototype states, reusing editable screen and component structures.
function cloneScreen(sourceKey,key,rowKey='states'){
  const source=figma.getNodeById(STATE.screens[sourceKey]);const n=track(source.clone());n.name=key+' / '+source.name.split(' / ').slice(1).join(' / ');
  let dest=figma.getNodeById(STATE.rows[rowKey]);if(dest.children.length>=5){const group=dest.parent;dest=group.children.find(x=>x.type==='FRAME'&&x.name.startsWith('Screens /')&&x.children.length<5)||row(group,'Screens / '+rowKey+' / '+group.children.length,2320,null,32);dest.counterAxisAlignItems='MIN';}dest.appendChild(n);screens[key]=n;return n;
}
function replaceText(root,before,after){for(const t of root.findAllWithCriteria({types:['TEXT']})){if(t.characters===before)t.characters=after;}}
{
const n=cloneScreen('home','home_50');replaceText(n,'25','50');const times=n.findOne(x=>x.name==='Duration presets');if(times){for(const c of times.children){if(c.name.includes('Time 25')){bindFill(c,'surface');for(const t of c.findAllWithCriteria({types:['TEXT']}))bindFill(t,'muted');}if(c.name.includes('Time 50')){bindFill(c,'soft');for(const t of c.findAllWithCriteria({types:['TEXT']}))bindFill(t,'primary');}}}
const thirty=cloneScreen('home','home_30');replaceText(thirty,'25','30');const preset=thirty.findOne(x=>x.name==='Duration presets');for(const c of preset.children){bindFill(c,c.name.includes('自訂')?'soft':'surface');for(const t of c.findAllWithCriteria({types:['TEXT']}))bindFill(t,c.name.includes('自訂')?'primary':'muted');}
}
{
const n=cloneScreen('focus','pomo_focus');replaceText(n,'單次專注 · 25 分鐘','番茄鐘 · 第 1 / 4 輪');
}
{
const n=cloneScreen('exit_wait','exit_ready');replaceText(n,'00:60','00:00');replaceText(n,'01:00','00:00');replaceText(n,'等滿 60 秒後，才能確認結束這次專注。','已完成等待，你仍可選擇繼續專注。');
const disabled=n.findOne(x=>x.type==='INSTANCE'&&x.findOne?.(t=>t.type==='TEXT'&&t.characters==='等待結束後可確認退出'));if(disabled){disabled.swapComponent(COMP['button_warning_Light']);setLabel(disabled,'確認提前結束');tag(disabled,'exit_end');}
}
{
const {body}=screen('exit_end',{title:'先告一段落，也沒關係',subtitle:'這次專注已提前結束。',row:'states'});spacer(body,32);const mark=row(body,'Exit illustration',364,96,0);mark.primaryAxisAlignItems='CENTER';icon(mark,'leaf',64,'warning');const summary=col(body,'Partial session summary',364,null,12,20,'surface',20);text(summary,'已專注 6 分鐘',24,'text','Bold',324);text(summary,'紀錄標示為「提前退出」，\n已投入的時間仍會保留。',14,'muted','Regular',324);divider(summary,324);text(summary,'退出原因',12,'muted','Medium');text(summary,'需要查看一份臨時文件。',14,'text','Regular',324);spacer(body);tag(button(body,'回到首頁'),'home');tag(button(body,'查看本次紀錄','secondary'),'history');}
{
const {body}=screen('interrupted',{title:'專注保護已中斷',subtitle:'無障礙服務已關閉，目前無法攔截其他 App。',row:'states'});spacer(body,40);const emblem=row(body,'Interrupted illustration',364,96,0);emblem.primaryAxisAlignItems='CENTER';icon(emblem,'shield',64,'warning');banner(body,'本次不會標示為完成','已保留中斷前 5 分鐘的專注紀錄。','warning');text(body,'重新開啟服務後，可以開始一段新的專注。\n系統設定與緊急通話始終可使用。',14,'muted','Regular',364);spacer(body);tag(button(body,'重新開啟服務'),'system_permission');tag(button(body,'查看紀錄','secondary'),'history');}
{
const n=cloneScreen('stats','stats_today');const tabs=n.findOne(x=>x.name==='統計區間');if(tabs)for(const c of tabs.children){if(c.name.startsWith('今日'))bindFill(c,'surface');else bindFill(c,'soft');}
const chart=n.findOne(x=>x.name==='七日專注圖表');if(chart){replaceText(chart,'近七日專注','今日專注');replaceText(chart,'6 小時 20 分','75 分鐘');replaceText(chart,'10 月 10 日 — 16 日','10 月 16 日');replaceText(chart,'每天，慢慢找回自己的節奏','09:00 · 50 分鐘 / 14:30 · 25 分鐘');const graph=chart.findOne(x=>x.name==='可編輯七日柱狀圖');if(graph){for(const [i,c]of [...graph.children].entries()){if(i!==0&&i!==6)c.visible=false;if(i===0){replaceText(c,'10','09:00');const bar=c.findOne(x=>x.type==='FRAME'&&x.name.startsWith('專注'));if(bar)bar.resize(64,47);}if(i===6){replaceText(c,'16','14:30');const bar=c.findOne(x=>x.type==='FRAME'&&x.name.startsWith('專注'));if(bar)bar.resize(64,23);}c.resize(150,104);}}}
for(const t of n.findAllWithCriteria({types:['TEXT']})){if(t.characters==='09:00'||t.characters==='14:30'){t.resize(60,18);t.textAutoResize='HEIGHT';}if(t.characters==='09:00 · 50 分鐘 / 14:30 · 25 分鐘')t.characters='09:00 / 14:30';}replaceText(n,'完成率  86%','完成率  100%');replaceText(n,'12 / 14 次專注','2 / 2 次專注');replaceText(n,'攔截  48 次','攔截  12 次');
}
{
const n=cloneScreen('exit_reason','exit_reason_empty');replaceText(n,'需要查看一份臨時文件。','寫下這次需要離開的原因…');replaceText(n,'12 / 100','0 / 100');const next=n.findOne(x=>x.type==='INSTANCE'&&x.name.includes('@exit_wait'));if(next){next.swapComponent(COMP['button_disabled_Light']);setLabel(next,'請先填寫退出原因');next.name='Disabled / Reason required';}const input=n.findOne(x=>x.name.includes('@reason_input'));if(input)input.name='Reason field @reason_fill';
}
const lightById=new Map([...vars].filter(([k])=>k.startsWith('color/Light/')).map(([k,v])=>[v.id,k.split('/')[2]]));
const mainById=new Map(Object.entries(STATE.components).map(([k,id])=>[id,k]));
for(const key of ['home','focus','stats']){
  const n=cloneScreen(key,key+'_dark','dark');theme='Dark';
  // Swap complete local component variants before applying semantic overrides.
  const topInstances=n.findAllWithCriteria({types:['INSTANCE']}).filter(i=>{let p=i.parent;while(p&&p!==n){if(p.type==='INSTANCE')return false;p=p.parent;}return true;});for(const i of topInstances){const source=i.mainComponent;if(source){const k=mainById.get(source.id);if(k&&k.endsWith('_Light')&&COMP[k.replace(/_Light$/,'_Dark')])i.swapComponent(COMP[k.replace(/_Light$/,'_Dark')]);}}
  for(const c of [n,...n.findAll(()=>true)])for(const prop of ['fills','strokes']){if(!(prop in c)||!Array.isArray(c[prop]))continue;const changed=c[prop].map(p=>{if(p.type!=='SOLID')return p;let role=lightById.get(p.boundVariables?.color?.id);if(!role){const match=Object.entries(colors.Light).find(([k,h])=>{const a=rgb(h);return Math.abs(a.r-p.color.r)<.002&&Math.abs(a.g-p.color.g)<.002&&Math.abs(a.b-p.color.b)<.002;});if(match)role=match[0];}return role?{...paint(role,'Dark'),opacity:p.opacity??1}:p;});c[prop]=changed;}
  track(n);theme='Light';
}
return finish();
