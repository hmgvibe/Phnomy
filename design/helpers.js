// Shared construction helpers for the editable Phnomy Figma document.
const CREATED = new Set();
const screens = {};
let theme = 'Light';
const colors = {
  Light: {bg:'#F7F7F2',surface:'#FFFFFF',text:'#202A24',muted:'#626F66',primary:'#274C3D',onPrimary:'#FFFFFF',soft:'#E9F0E7',line:'#DFE5DC',warning:'#92592B',warningSoft:'#F5EBDD'},
  Dark: {bg:'#121C17',surface:'#1B2921',text:'#ECF1E8',muted:'#B4C1B6',primary:'#ACCCB4',onPrimary:'#183324',soft:'#2A3D30',line:'#3B5041',warning:'#E7B887',warningSoft:'#3B3024'}
};
const vars = new Map((await figma.variables.getLocalVariablesAsync()).map(v=>[v.name,v]));
const styles = new Map((await figma.getLocalTextStylesAsync()).map(s=>[s.name,s]));
await Promise.all(['Regular','Medium','Bold'].map(style=>figma.loadFontAsync({family:'Noto Sans TC',style})));
await Promise.all(['Regular','Light','Medium'].map(style=>figma.loadFontAsync({family:'Roboto Mono',style})));
const COMP = {};
for (const [key,id] of Object.entries(STATE.components || {})) COMP[key] = await figma.getNodeByIdAsync(id);
function track(n) { CREATED.add(n.id); if ('findAll' in n) for (const d of n.findAll(()=>true)) CREATED.add(d.id); return n; }
function rgb(h) { return {r:parseInt(h.slice(1,3),16)/255,g:parseInt(h.slice(3,5),16)/255,b:parseInt(h.slice(5,7),16)/255}; }
function paint(role='text',t=theme) { const p={type:'SOLID',color:rgb(colors[t][role]||role)}; const v=vars.get('color/'+t+'/'+role); return v?figma.variables.setBoundVariableForPaint(p,'color',v):p; }
function bindFill(n,role,t=theme) { n.fills=[paint(role,t)]; }
function radius(n,r) { n.cornerRadius=r; const v=vars.get('radius/'+r);if(v)for(const p of ['topLeftRadius','topRightRadius','bottomLeftRadius','bottomRightRadius'])n.setBoundVariable(p,v); }
function gap(n,v) { n.itemSpacing=v;const a=vars.get('space/'+v);if(a)n.setBoundVariable('itemSpacing',a); }
function padding(n,v) { for(const p of ['paddingLeft','paddingRight','paddingTop','paddingBottom']){n[p]=v;const a=vars.get('space/'+v);if(a)n.setBoundVariable(p,a);} }
function layout(parent,name,d,w=364,h=null,g=8,p=0,bg=null,r=0) {const n=track(figma.createAutoLayout(d));n.name=name;n.fills=[];n.resize(w,h||1);n.layoutSizingHorizontal='FIXED';n.layoutSizingVertical=h===null?'HUG':'FIXED';gap(n,g);padding(n,p);if(bg)bindFill(n,bg);if(r)radius(n,r);n.clipsContent=false;if(parent)parent.appendChild(n);return n;}
function col(parent,name,w=364,h=null,g=8,p=0,bg=null,r=0){return layout(parent,name,'VERTICAL',w,h,g,p,bg,r);}
function row(parent,name,w=364,h=null,g=8,p=0,bg=null,r=0){const n=layout(parent,name,'HORIZONTAL',w,h,g,p,bg,r);n.counterAxisAlignItems='CENTER';return n;}
function box(parent,name,w,h,bg='surface',r=16) {const n=track(figma.createFrame());n.name=name;n.resize(w,h);n.fills=bg?[paint(bg)]:[];if(r)radius(n,r);if(parent)parent.appendChild(n);return n;}
function text(parent,value,size=14,role='text',weight='Regular',width=null){const n=track(figma.createText());n.name='Text / '+value.replace(/\n/g,' ').slice(0,35);n.fontName={family:'Noto Sans TC',style:weight};n.fontSize=size;n.lineHeight={unit:'PIXELS',value:Math.ceil(size*1.5)};n.characters=value;bindFill(n,role);const s=styles.get('Type/'+size+'/'+weight);if(s)n.textStyleId=s.id;if(width!==null){n.resize(width,Math.ceil(size*1.5));n.textAutoResize='HEIGHT';}else n.textAutoResize='WIDTH_AND_HEIGHT';if(parent)parent.appendChild(n);return n;}
function mono(parent,value,size=48,role='text'){const n=text(parent,value,size,role);n.fontName={family:'Roboto Mono',style:'Regular'};n.lineHeight={unit:'PIXELS',value:Math.ceil(size*1.2)};const s=styles.get('Mono/'+size);if(s)n.textStyleId=s.id;return n;}
function spacer(parent,h=null){const n=box(parent,'Space',1,h||1,null,0);if(h===null)n.layoutSizingVertical='FILL';return n;}
function tag(n,action){n.name+=' @'+action;return n;}
const iconPaths={
  focus:'M12 3a9 9 0 1 0 9 9M12 7a5 5 0 1 0 5 5M12 11a1 1 0 1 0 1 1M16 3h5v5M21 3l-9 9',
  chart:'M4 20h16M6 16v-5M12 16V4M18 16V8',settings:'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8M9 3h6l1 3 3 1 2 5-2 5-3 1-1 3H9l-1-3-3-1-2-5 2-5 3-1 1-3',
  chevron:'m9 6 6 6-6 6',back:'m14 6-6 6 6 6',check:'m5 12 4 4L19 6',phone:'M7 3H4v4c0 7.2 5.8 13 13 13h4v-3l-5-2-2 2a14 14 0 0 1-7-7l2-2-2-5Z',
  map:'m3 6 6-3 6 3 6-3v15l-6 3-6-3-6 3V6ZM9 3v15M15 6v15',message:'M4 4h16v12H9l-5 4V4ZM8 8h8M8 12h5',shield:'m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6l8-3Zm-4 9 3 3 5-6',
  clock:'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0ZM12 7v5l3 2',moon:'M20 14a8 8 0 0 1-10-10 9 9 0 1 0 10 10Z',search:'M17 10a7 7 0 1 1-14 0 7 7 0 0 1 14 0Zm-2 5 6 6',
  arrow:'M4 12h16m-6-6 6 6-6 6',play:'m8 4 12 8-12 8V4Z',lock:'M6 10h12v11H6V10ZM8 10V6a4 4 0 0 1 8 0v4M12 14v3',info:'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0ZM12 11v6M12 7v.01',
  history:'M3 11a9 9 0 1 1 2 7M3 4v7h7M12 7v5l3 2',sun:'M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0ZM12 2v2M12 20v2M2 12h2M20 12h2M5 5l1 1M18 18l1 1M5 19l1-1M18 6l1-1',
  leaf:'M20 3C9 2 3 7 5 14c3 8 14 6 15-11ZM4 21l11-11',close:'m6 6 12 12M6 18 18 6',wifi:'M3 8a15 15 0 0 1 18 0M6 12a10 10 0 0 1 12 0M9 16a5 5 0 0 1 6 0M12 20h.01',
  signal:'M4 20v-4M9 20V12M14 20V8M19 20V4',battery:'M3 7h16v10H3V7ZM21 10v4M6 10h10v4H6v-4',bell:'M6 9a6 6 0 0 1 12 0v7l2 2H4l2-2V9ZM10 21h4',
  video:'M3 5h18v14H3V5Zm7 4 5 3-5 3V9Z',instagram:'M7 3h10a4 4 0 0 1 4 4v10a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4V7a4 4 0 0 1 4-4ZM16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0ZM17 7h.01',globe:'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0ZM3 12h18M12 3c5 5 5 13 0 18-5-5-5-13 0-18',
  plus:'M12 5v14M5 12h14',minus:'M5 12h14'
};
function icon(parent,name,size=24,role='primary'){const svg='<svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="'+(iconPaths[name]||iconPaths.info)+'" stroke="'+colors[theme][role]+'" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round"/></svg>';const n=track(figma.createNodeFromSvg(svg));n.name='Icon / '+name;n.resize(size,size);if(parent)parent.appendChild(n);for(const v of n.findAll(x=>x.type==='VECTOR'))v.strokes=[paint(role)];return n;}
function instance(parent,key,w=null){const main=COMP[key];if(!main)throw new Error('Missing component '+key);const n=track(main.createInstance());if(parent)parent.appendChild(n);if(w!==null)n.resize(w,n.height);return n;}
function setLabel(n,value,name='Label'){const ts=n.findAll(x=>x.type==='TEXT'&&x.name===name);for(const t of ts)t.characters=value;return n;}
function button(parent,label,kind='primary',w=364){const key='button_'+kind+'_'+theme;const n=instance(parent,key,w);setLabel(n,label);return n;}
function pill(parent,label,role='primary'){const p=row(parent,'Badge',1,28,4,0,role==='warning'?'warningSoft':'soft',999);p.layoutSizingHorizontal='HUG';p.paddingLeft=10;p.paddingRight=10;text(p,label,12,role,'Medium');return p;}
function divider(parent,w=364){return box(parent,'Divider',w,1,'line',0);}
function appIcon(parent,name,size=40){const key='appIcon_'+name+'_'+theme;if(COMP[key]){const n=instance(parent,key);n.name='App / '+name;if(size!==40)n.resize(size,size);return n;}const n=row(parent,'App / '+name,size,size,0,0,'soft',12);n.primaryAxisAlignItems='CENTER';icon(n,name==='電話'?'phone':name==='Google 地圖'?'map':name==='LINE'?'message':name==='Instagram'?'instagram':name==='YouTube'?'video':'globe',22);return n;}
function appRow(parent,name,detail,selected=true){const n=instance(parent,'appRow_'+(selected?'selected':'unselected')+'_'+theme);setLabel(n,name,'Name');setLabel(n,detail,'Detail');const slot=n.findOne(x=>x.type==='INSTANCE'&&x.name.startsWith('App /'));if(slot&&COMP['appIcon_'+name+'_'+theme])slot.swapComponent(COMP['appIcon_'+name+'_'+theme]);return n;}
function modeSwitch(parent,pomodoro=false){const n=instance(parent,'mode_'+(pomodoro?'pomodoro':'single')+'_'+theme);return n;}
function shortcutRow(parent){const r=row(parent,'Allowed apps',364,72,12);r.primaryAxisAlignItems='CENTER';for(const name of ['電話','LINE','Google 地圖']){const a=col(r,name,72,72,4);a.counterAxisAlignItems='CENTER';appIcon(a,name,40);text(a,name,12,'muted');}return r;}
function status(parent){const n=row(parent,'Android / status bar',412,32,4,0);n.paddingLeft=24;n.paddingRight=24;text(n,'9:41',12,'text','Medium');spacer(n,null).layoutSizingHorizontal='FILL';icon(n,'signal',14,'text');icon(n,'wifi',14,'text');icon(n,'battery',18,'text');return n;}
function header(body,title,subtitle,back=false){const h=col(body,'Page header',364,null,6);if(back){const b=row(h,'Back',364,40,8);tag(b,'back');icon(b,'back',20);text(b,'返回',14,'muted');}text(h,title,28,'text','Bold',364);if(subtitle)text(h,subtitle,14,'muted','Regular',364);return h;}
function nav(parent,active){const n=instance(parent,'nav_'+active+'_'+theme);return n;}
function screen(key,opts={}){const dest=STATE.rows[opts.row||'main'];let parent=figma.getNodeById(dest);if(!parent)throw new Error('Missing parent '+dest);if(parent.children.length>=5){const group=parent.parent;const other=group.children.find(n=>n.type==='FRAME'&&n.name.startsWith('Screens /')&&n.children.length<5);parent=other||row(group,'Screens / '+(opts.row||'main')+' / '+group.children.length,2320,null,32);parent.counterAxisAlignItems='MIN';}const n=col(parent,key+' / '+(opts.title||'專注'),412,915,0,0,'bg',0);n.clipsContent=true;screens[key]=n;status(n);const body=col(n,'Content',412,opts.nav?795:859,16,24);body.paddingTop=16;body.paddingBottom=16;const inner=col(body,'Content stack',364,opts.nav?763:827,16);if(opts.title)header(inner,opts.title,opts.subtitle,opts.back);if(opts.nav)nav(n,opts.nav);const gesture=row(n,'Android / gesture area',412,24,0);gesture.primaryAxisAlignItems='CENTER';const bar=box(gesture,'Gesture handle',104,4,'text',99);return {frame:n,body:inner};}
function timer(parent,value,phase='focus'){const n=instance(parent,'timer_'+phase+'_'+theme);setLabel(n,value,'Time');return n;}
function banner(parent,title,detail,kind='normal'){const n=col(parent,'Notice',364,null,4,16,kind==='warning'?'warningSoft':'soft',16);text(n,title,14,kind==='warning'?'warning':'primary','Medium',332);if(detail)text(n,detail,12,'muted','Regular',332);return n;}
function finish(){return {createdNodeIds:[...CREATED],screens:Object.fromEntries(Object.entries(screens).map(([k,n])=>[k,n.id]))};}
