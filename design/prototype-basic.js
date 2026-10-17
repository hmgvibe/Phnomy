// Starter-compatible prototype: one navigation action per interaction.
const mutated=[];const wired=[];const skipped=[];const ids={...STATE.screens};let branchIndex=0;
function duplicate(sourceKey,key){const source=figma.getNodeById(ids[sourceKey]);const n=track(source.clone());figma.currentPage.appendChild(n);n.x=2720+(branchIndex%6)*444;n.y=480+Math.floor(branchIndex/6)*1000;branchIndex++;n.name=key+' / '+source.name.split(' / ').slice(1).join(' / ');screens[key]=n;ids[key]=n.id;return n;}
function replace(root,a,b){for(const t of root.findAllWithCriteria({types:['TEXT']}))if(t.characters===a){t.characters=b;mutated.push(t.id);}}
if(BUILD_PHASE==='build_a'){
for(const[key,label,time]of [['focus_50','單次專注 · 50 分鐘','43:42'],['focus_30','單次專注 · 30 分鐘','23:42']]){const n=duplicate('focus',key);replace(n,'單次專注 · 25 分鐘',label);replace(n,'18:42',time);}
const home4=duplicate('home','home_4');replace(home4,'電話、LINE、Google 地圖','電話、LINE、地圖等 4 個 App');
const next=duplicate('pomo_focus','pomo_focus_next');replace(next,'番茄鐘 · 第 1 / 4 輪','番茄鐘 · 第 2 / 4 輪');replace(next,'18:42','25:00');const nextTimer=next.findOne(x=>x.name.endsWith('@break'));if(nextTimer)nextTimer.name=nextTimer.name.replace(' @break','');
for(const ctx of ['_50','_30','_pomo'])for(const base of ['permissions','permissions_ready','system_permission'])duplicate(base,base+ctx);
}
if(BUILD_PHASE==='build_b')branchIndex=25;
if(BUILD_PHASE!=='wire')for(const ctx of BUILD_PHASE==='build_a'?['_50','_30','_pomo']:['_break','_pomo_next','_dark'])for(const base of ['exit_reason_empty','exit_reason','exit_wait','exit_ready'])duplicate(base,base+ctx);
if(BUILD_PHASE!=='wire')return {...finish(),mutatedNodeIds:mutated};
const contextByOwner={focus:'',focus_50:'_50',focus_30:'_30',pomo_focus:'_pomo',break:'_break',pomo_focus_next:'_pomo_next',focus_dark:'_dark'};
const targetByContext={'':'focus',_50:'focus_50',_30:'focus_30',_pomo:'pomo_focus',_break:'break',_pomo_next:'pomo_focus_next',_dark:'focus_dark'};
const pomo=figma.getNodeById(ids.pomodoro);const rhythm=pomo.findOne(n=>n.name==='Pomodoro rhythm');if(rhythm){gap(rhythm,6);mutated.push(rhythm.id);}const pb=pomo.findOne(n=>n.name==='Content stack');gap(pb,12);mutated.push(pb.id);
const sheets=new Set(['custom','pomodoro_settings','allowed_apps','dnd_info','theme','clear_data','system_permission','system_permission_50','system_permission_30','system_permission_pomo']);
const closeActions=new Set(['sheet_custom_cancel','sheet_pomodoro_save','sheet_pomodoro_cancel','sheet_allowed_return','sheet_dnd_close','sheet_theme_system','sheet_theme_close','sheet_clear_cancel','sheet_permission_back']);
const direct={sheet_custom_apply:'home_30',sheet_theme_light:'home',sheet_theme_dark:'home_dark',sheet_clear_confirm:'stats_empty',stats_week:'stats',stats_today:'stats_today',dnd_on:'home',unselect_Instagram:'whitelist',select_Instagram:'whitelist_4'};
const startEntries={home:'',home_4:'',home_quiet_off:'',home_50:'_50',home_30:'_30',pomodoro:'_pomo'};
const ownerContext=owner=>{for(const c of ['_pomo_next','_break','_pomo','_dark','_50','_30'])if(owner.endsWith(c))return c;return '';};
function node(key,overlay=false){return{type:'NODE',destinationId:ids[key],navigation:overlay?'OVERLAY':'NAVIGATE',transition:{type:'DISSOLVE',easing:{type:'EASE_OUT'},duration:.2},resetScrollPosition:true};}
for(const[owner,id]of Object.entries(ids)){
 const frame=figma.getNodeById(id);const ctx=ownerContext(owner);
 for(const c of frame.findAll(n=>n.name.includes('@'))){const a=c.name.split('@').pop();let action=null;
   if(closeActions.has(a))action={type:'CLOSE'};
   else if(a==='permissions'&&owner in startEntries)action=node('permissions'+startEntries[owner]);
   else if(a==='permissions'&&owner==='home_dark')action=node('focus_dark');
   else if(a==='focus')action=node(owner.startsWith('permissions_ready')?targetByContext[ctx]||'focus':owner==='break'?'pomo_focus_next':targetByContext[ctx]||'focus');
   else if(a==='exit_reason')action=node('exit_reason_empty'+(contextByOwner[owner]||''));
   else if(a==='reason_fill'||a==='reason_input')action=node('exit_reason'+ctx);
   else if(a==='exit_wait')action=node('exit_wait'+ctx);
   else if(a==='break')action=node(owner==='pomo_focus'?'break':'complete');
   else if(a==='back')action=node(owner==='history'?'stats':owner.startsWith('exit_reason')?targetByContext[ctx]||'focus':'home');
   else if(a==='system_permission')action=node('system_permission'+(['_50','_30','_pomo'].includes(ctx)?ctx:''),true);
   else if(a==='sheet_permission_ready')action=node('permissions_ready'+(['_50','_30','_pomo'].includes(ctx)?ctx:''));
   else if(a==='dnd_toggle')action=owner==='home'?node('home_quiet_off'):node('dnd_info',true);
   else if(a==='usage_access'||(a==='stats_no_access'&&owner==='settings'))action=node('system_permission',true);
   else if(a==='home'&&owner==='whitelist_4')action=node('home_4');
   else if(a in direct)action=node(direct[a]);
   else if(a in ids)action=node(a,sheets.has(a));
   if(!action){skipped.push({owner,action:a,nodeId:c.id});continue;}
   if(action.type==='NODE'&&action.destinationId===id)continue;
   await c.setReactionsAsync([{trigger:{type:'ON_CLICK'},actions:[action]}]);mutated.push(c.id);wired.push({owner,action:a,nodeId:c.id,target:action.destinationId||'CLOSE'});
 }
}
for(const ctx of ['', '_50','_30','_pomo','_break','_pomo_next','_dark']){const wait=figma.getNodeById(ids['exit_wait'+ctx]);await wait.setReactionsAsync([{trigger:{type:'AFTER_TIMEOUT',timeout:60},actions:[node('exit_ready'+ctx)]}]);mutated.push(wait.id);}
const guide=figma.getNodeById(STATE.guide);for(const c of guide.findAll(x=>x.name.includes('@go_'))){const key=c.name.split('@go_').pop();await c.setReactionsAsync([{trigger:{type:'ON_CLICK'},actions:[node(key)]}]);mutated.push(c.id);wired.push({owner:'guide',action:key,nodeId:c.id,target:ids[key]});}
figma.currentPage.flowStartingPoints=[{nodeId:ids.home,name:'01 · 完整專注流程'},{nodeId:ids.pomodoro,name:'02 · 番茄鐘'},{nodeId:ids.blocked,name:'03 · 攔截提示'},{nodeId:ids.exit_reason_empty,name:'04 · 提前退出（等待 60 秒）'},{nodeId:ids.stats_no_access,name:'05 · 使用資料未授權'},{nodeId:ids.interrupted,name:'06 · 權限中斷'}];
return {...finish(),mutatedNodeIds:mutated,wiredCount:wired.length,skippedActions:[...new Set(skipped.map(x=>x.action))],flowStartingPoints:figma.currentPage.flowStartingPoints};
