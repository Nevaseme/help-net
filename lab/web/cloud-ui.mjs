import {usDateTime,usTime} from './date-time.mjs';
import {CloudClient} from './cloud-client.mjs';
import {setText as rawSetText,setProperty} from './dom-state.mjs';
import {translateUI,bindStaticUiLanguage,observeUiLanguage} from './web1617-english.mjs';
const setText=(element,text)=>rawSetText(element,translateUI(text));
import {MUNICIPALITIES} from './municipalities.mjs';

/** Reuses the existing registration/incident screens; only the connection controls differ. */
export async function attachCloudUi({store,id,getGroup,setGroup,refresh,tab,onJoined}){
 const $=key=>document.getElementById(key),host=document.createElement('div');
 host.className='cloud-panel';
 host.innerHTML=`<h3>参加中のグループ</h3>
 <p id="cloud-group-state">管理者から案内された参加情報を入力してください。</p>
 <p id="cloud-city-state"></p><form id="cloud-city-form" hidden><label>活動地域の市<select id="cloud-city"><option value="">市を選ぶ</option>${MUNICIPALITIES.map(c=>`<option value="${c.code}">${c.prefecture} ${c.name}</option>`).join('')}</select></label><button type="submit" class="secondary">活動地域を保存</button></form>
 <details id="cloud-membership" open><summary>別のグループに参加</summary>
 <h3 id="cloud-registration-title">グループに参加</h3>
 <form id="group-access"><p id="group-access-help" class="muted">管理者から案内されたグループ名と参加パスワードを入力してください。</p><label><span id="group-identity-label">グループ名</span><input id="group-join-identity" required maxlength="80" autocomplete="off" autocapitalize="none" spellcheck="false"></label><label id="group-password-label">参加パスワード<input id="group-join-password" type="password" required maxlength="128" autocomplete="off"></label><button id="group-next" type="submit">次へ</button><button id="group-join-method" type="button" class="text-button">招待コードで参加する</button></form>
 <form id="group-profile" hidden><p id="group-joining-name" class="muted"></p><label>あなたの名前<input id="group-join-name" required maxlength="60" autocomplete="nickname"></label><p class="muted">グループのメンバーに表示する名前です。</p><button id="group-join" type="submit">参加する</button><button id="group-back" type="button" class="text-button">参加情報に戻る</button></form><p id="group-join-feedback" class="feedback" role="alert"></p></details>
 <div class="actions"><button id="cloud-sync" type="button">今すぐ共有</button><button id="cloud-invite" type="button" class="secondary" hidden>招待コードを作る</button></div>
 <label id="cloud-invite-result" hidden>参加する人へ渡すコード<input id="cloud-invite-output" readonly><small id="cloud-invite-expiry"></small></label>
 <p id="cloud-status" class="feedback" role="status" aria-live="polite"></p>
 <p class="muted">端末に保存した記録を共有サーバーへ送ります。圏外の記録は端末に残り、画面を開く・通信が戻ると再送します。</p>`;
 $('settings').querySelector('h2').after(host);
 const recoverLink=document.createElement('button');recoverLink.id='group-recover-method';recoverLink.type='button';recoverLink.className='text-button';recoverLink.textContent='以前の参加を復元する';const joinHelp=document.createElement('details');joinHelp.id='join-help';joinHelp.className='join-help';joinHelp.innerHTML='<summary>参加できないとき</summary>';joinHelp.append($('group-join-method'),recoverLink);$('group-access').append(joinHelp);
 const recoveryLabel=document.createElement('label');recoveryLabel.hidden=true;recoveryLabel.id='group-recovery-label';recoveryLabel.innerHTML='復元コード<input id="group-recovery-code" maxlength="40" autocomplete="off" autocapitalize="characters" spellcheck="false">';$('group-next').before(recoveryLabel);
 const accessPanel=document.createElement('section');accessPanel.id='member-access';accessPanel.innerHTML='<h3>参加の復元・管理</h3><p class="muted">復元コードは24時間以内に1回使えます。復元すると古い端末の接続は停止します。</p><label id="access-member-label" hidden>参加者<select id="access-member"></select></label><button id="access-issue" type="button" class="secondary">復元コードを発行</button><button id="access-revoke" type="button" class="text-button" hidden>この参加登録を停止</button><label id="access-code-label" hidden>本人だけに渡す復元コード<input id="access-code" readonly></label><p id="access-feedback" role="status"></p>';host.append(accessPanel);
 const communication=document.createElement('section');communication.id='cloud-communication';communication.innerHTML='<h3>通信の詳細</h3>';
 communication.append($('cloud-status'),$('cloud-sync'),$('receipts'),$('sync-error'));host.append(communication);
 const description=host.querySelector(':scope > .muted');description.textContent='記録は自動で共有されます。圏外で保存した記録は、通信が戻ると送信します。';communication.append(description);
 setText($('cloud-sync'),'今すぐ同期');document.querySelector('.sharing-details').hidden=true;

 const venueAdmin=document.createElement('form');venueAdmin.id='venue-admin';venueAdmin.hidden=true;
 venueAdmin.noValidate=true;
 venueAdmin.innerHTML='<h3>会場訓練</h3><p></p><button id="venue-start" type="submit">訓練を開始する</button><p id="venue-admin-state" class="muted"></p>';
 const modeLabel=document.createElement('label');modeLabel.innerHTML='地図上の位置<select id="venue-position-mode"><option value="KYOTO">京都の割当位置</option><option value="ACTUAL">参加者の現在地</option></select>';venueAdmin.querySelector('h3').after(modeLabel);modeLabel.hidden=true;venueAdmin.querySelector('h3').remove();venueAdmin.querySelector('p').textContent='開始時の経過時間を設定します。開始後も変更できます。';
 const presentation=document.createElement('label');presentation.innerHTML='モード<select id="venue-presentation-mode"><option value="DRILL">訓練</option><option value="EARTHQUAKE">地震</option></select>';modeLabel.before(presentation);
 const updateStartLabel=()=>setText($('venue-start'),$('venue-presentation-mode').value==='EARTHQUAKE'?'地震を開始する':'訓練を開始する');presentation.onchange=updateStartLabel;observeUiLanguage(updateStartLabel);
 const initialClock=document.createElement('label');initialClock.innerHTML='<span>開始時の経過分数</span><input id="venue-initial-minutes" type="number" min="0" max="1440" step="1" value="3" required inputmode="numeric">';venueAdmin.querySelector('#venue-start').before(initialClock);
 const venueDetails=document.createElement('section');venueDetails.className='settings-section';venueDetails.append(venueAdmin);const venueFeedback=document.createElement('p');venueFeedback.id='venue-feedback';venueFeedback.setAttribute('role','alert');venueDetails.append(venueFeedback);host.insertBefore(venueDetails,$('cloud-membership'));
 const overview=document.createElement('div');overview.id='group-demo-overview';overview.innerHTML='<p id="group-demo-state"></p><p id="group-demo-mode" class="muted"></p><button id="group-open-safety" type="button">安否を回答する</button><button id="venue-end" type="button" class="text-button" hidden>訓練を終了する</button>';host.insertBefore(overview,venueDetails);
 const clock=document.createElement('form');clock.id='venue-clock';clock.hidden=true;clock.innerHTML='<label><span data-clock-label></span><input id="venue-clock-minutes" type="number" min="0" max="1440" step="1" required inputmode="numeric"></label><button type="submit" class="secondary" data-clock-apply></button><p role="status" id="venue-clock-feedback"></p>';overview.insertBefore(clock,$('venue-end'));
 clock.onsubmit=async e=>{e.preventDefault();if(busy)return;busy=true;try{await client.setVenueClock(getGroup(),Number($('venue-clock-minutes').value));await refresh();$('venue-clock-feedback').textContent=document.documentElement.lang==='en'?'Time updated':'経過時間を変更しました';}catch(e){$('venue-clock-feedback').textContent=e.message;}finally{busy=false;await controls();}};
 const groupDetails=document.createElement('section');groupDetails.id='group-details';groupDetails.className='settings-section';groupDetails.innerHTML='<h3>グループの設定</h3><p class="muted" id="group-settings-purpose"></p>';groupDetails.append($('cloud-city-state'),$('cloud-city-form'),$('cloud-invite'),$('cloud-invite-result'),$('cloud-membership'));host.append(accessPanel,groupDetails,communication);const permissionPanel=document.createElement('section');permissionPanel.id='member-permissions';groupDetails.prepend(permissionPanel);
 let groupPage='overview',groupAvailable=false,accessAvailable=false,adminSetupAvailable=false;
 const groupBack=document.createElement('button');groupBack.type='button';groupBack.className='text-button';groupBack.id='group-page-back';groupBack.textContent='グループに戻る';host.prepend(groupBack);
 const settingsLink=document.createElement('button');settingsLink.type='button';settingsLink.className='secondary';settingsLink.id='group-settings-open';settingsLink.textContent='グループの設定';host.append(settingsLink);
 const accessLink=document.createElement('button');accessLink.type='button';accessLink.className='text-button';accessLink.textContent='参加の復元・管理';groupDetails.append(accessLink);
 const communicationLink=document.createElement('button');communicationLink.type='button';communicationLink.className='text-button';communicationLink.textContent='通信の詳細';groupDetails.append(communicationLink);
 const eventControls=document.createElement('section');eventControls.id='event-controls';
 document.getElementById('training-page').prepend(eventControls);eventControls.append(venueDetails,clock,$('venue-end'));
 const eventLink=document.createElement('button');eventLink.type='button';eventLink.className='secondary';eventLink.id='group-events-open';overview.append(eventLink);
 eventLink.onclick=()=>{tab('settings');document.body.dataset.settingsView='incidents';$('settings').querySelector('h2').textContent=translateUI('訓練・地震');};
 function renderGroupPage(){
  eventLink.textContent=translateUI('訓練・地震');$('group-settings-purpose').textContent=document.documentElement.lang==='en'?'The city is used for offline maps and address searches. This does not change neighborhood boundaries.':'地図保存と住所検索に使う市です。町内会の範囲は変わりません。';

  setProperty(overview,'hidden',!groupAvailable||groupPage!=='overview');setProperty(venueDetails,'hidden',!adminSetupAvailable);
  setProperty(groupDetails,'hidden',!groupAvailable||groupPage!=='settings');setProperty(accessPanel,'hidden',!accessAvailable||groupPage!=='access');
  setProperty(communication,'hidden',!groupAvailable||groupPage!=='communication');setProperty(settingsLink,'hidden',!groupAvailable||groupPage!=='overview');setProperty(groupBack,'hidden',!groupAvailable||groupPage==='overview');setProperty(accessLink,'hidden',!accessAvailable);
 }
 function openGroupPage(page){groupPage=page;renderGroupPage();groupBack.focus();}
 settingsLink.onclick=()=>openGroupPage('settings');accessLink.onclick=()=>openGroupPage('access');communicationLink.onclick=()=>openGroupPage('communication');groupBack.onclick=()=>{openGroupPage('overview');settingsLink.focus();};
 $('group').readOnly=true;
 setProperty($('legacy-connection'),'hidden',true);

 bindStaticUiLanguage(host);bindStaticUiLanguage(eventControls);observeUiLanguage(()=>controls());
 document.title='Help Net';

 $('incident-state').nextElementSibling.textContent='グループの地震発生・安否・対応記録を共有します。速報を受信したAndroidからの開始情報も取得します。';bindStaticUiLanguage($('incident-state').nextElementSibling);
 $('incident-form').querySelector('p').textContent=translateUI('グループの必要人数の報告が集まると開始します（参加者の3分の1、最低2人・最大10人）。終了は管理者が行います。');
 $('group').parentElement.hidden=true;
 const client=new CloudClient(store,id);let stopped=false,busy=false,timer=null,generation=0,inFlight=null;
 const status=(text,summary)=>{setText($('cloud-status'),text);setText($('network'),summary||text);setText($('sharing'),text);};
 const resetTimer=()=>{clearTimeout(timer);if(!stopped&&!document.hidden)timer=setTimeout(()=>run(),15000);};
 async function activityName(session){return session?.activityName||(await store.roster(session?.groupId??getGroup()))?.members.find(m=>m.deviceIds.includes(id))?.activityName||'';}
 let cityDraftGroup=null,cityDirty=false;$('cloud-city').onchange=()=>{cityDirty=true;};
 $('cloud-city-form').onsubmit=async event=>{event.preventDefault();if(busy||!$('cloud-city').value)return;busy=true;await controls();try{await inFlight;await client.setMunicipality(getGroup(),$('cloud-city').value);cityDirty=false;status('活動地域を保存しました');}catch(e){status(e.message);}finally{busy=false;await controls();await refresh();}};
 async function controls(){const session=await client.session(getGroup());document.body.classList.toggle('registration-needed',!session);setText(host.querySelector(':scope > h3'),session?.groupName||'グループ');setText($('cloud-group-state'),session?`${await activityName(session)}${session.role==='ADMIN'?' · 管理者':''}`:'管理者から案内された参加情報を入力してください。');setText($('header-group'),session?`${session.groupName} / ${await activityName(session)}`:'安否と対応の共有');setProperty($('cloud-sync'),'disabled',busy||!session);setProperty($('cloud-invite'),'hidden',session?.role!=='ADMIN');setProperty($('cloud-invite'),'disabled',busy);for(const button of host.querySelectorAll('form button'))setProperty(button,'disabled',busy);for(const button of document.querySelectorAll('[data-stop]'))button.disabled=!session;
  const city=await store.read('meta','municipality:'+getGroup());setProperty($('cloud-city-state'),'hidden',!session);setText($('cloud-city-state'),`活動地域：${city?.name||'未設定'}`);setProperty($('cloud-city-form'),'hidden',session?.role!=='ADMIN');setProperty($('cloud-city'),'disabled',busy);
  if(cityDraftGroup!==getGroup()||!cityDirty){$('cloud-city').value=city?.code||'';cityDraftGroup=getGroup();cityDirty=false;}
  const venue=await store.read('meta','venue:'+getGroup());const live=!!venue&&!venue.closed;
  const english=document.documentElement.lang==='en';setProperty(clock,'hidden',!live||session?.role!=='ADMIN');setText(clock.querySelector('[data-clock-label]'),english?'Minutes since the earthquake':'地震発生からの経過分数');setText(clock.querySelector('[data-clock-apply]'),english?'Apply time':'経過時間を変更');setProperty(clock.querySelector('button'),'disabled',busy);
  const clockKey=venue?.sessionId+':'+venue?.simulationStartedAt;if(clock.dataset.clockKey!==clockKey){clock.dataset.clockKey=clockKey;$('venue-clock-minutes').value=String(Math.max(0,Math.floor((Date.now()-(venue?.simulationStartedAt??venue?.startedAt??Date.now()))/60000)));}venueAdmin.hidden=session?.role!=='ADMIN'||live;groupAvailable=!!session;adminSetupAvailable=!venueAdmin.hidden;setText($('group-demo-state'),live?`${venue.presentationMode==='EARTHQUAKE'?'地震':'訓練'}開催中 · ${venue.placements.length}人`:session?.role==='ADMIN'?'訓練を準備しましょう':'管理者の訓練開始を待っています');setText($('group-demo-mode'),live?(venue.positionMode==='ACTUAL'?'参加者の現在地を表示':'京都の割当位置を表示'):session?.role==='ADMIN'?'チーム内の練習も、本番も同じ操作で始められます。':'参加情報とグループの設定を確認できます。');setProperty($('group-open-safety'),'hidden',true);setText($('venue-end'),venue?.presentationMode==='EARTHQUAKE'?'地震を終了する':'訓練を終了する');setProperty($('venue-end'),'hidden',!live||session?.role!=='ADMIN');setProperty($('venue-end'),'disabled',busy);setText($('venue-admin-state'),venue&&!venue.closed?`${venue.presentationMode==='EARTHQUAKE'?'地震':'訓練'}開催中 · ${venue.placements.length}人`:'');setProperty($('venue-start'),'disabled',busy||!!(venue&&!venue.closed));
  accessAvailable=!!session&&!!venue&&!venue.closed&&!!venue.recoveryAvailable;setProperty($('access-member-label'),'hidden',session?.role!=='ADMIN');setProperty($('access-revoke'),'hidden',session?.role!=='ADMIN');
  if(session?.role==='ADMIN'){const roster=await store.roster(getGroup()),members=roster?.members.filter(m=>m.role==='MEMBER')??[],signature=JSON.stringify(members.map(m=>[m.memberId,m.activityName,venue?.inactiveMemberIds?.includes(m.memberId)]));if($('access-member').dataset.members!==signature){const selected=$('access-member').value;$('access-member').replaceChildren(...members.map(m=>new Option(m.activityName+(venue?.inactiveMemberIds?.includes(m.memberId)?'（停止中）':''),m.memberId)));if(members.some(m=>m.memberId===selected))$('access-member').value=selected;$('access-member').dataset.members=signature;}}
  for(const key of ['access-issue','access-revoke'])setProperty($(key),'disabled',busy||session?.role==='ADMIN'&&!$('access-member').value);
  permissionPanel.hidden=session?.role!=='ADMIN';
  if(!permissionPanel.hidden){const roster=await store.roster(getGroup()),signature=JSON.stringify([roster?.revision,busy,english]);if(permissionPanel.dataset.signature!==signature){permissionPanel.dataset.signature=signature;permissionPanel.replaceChildren();const heading=document.createElement('h3');heading.textContent=english?'Injury and urgency permissions':'負傷登録・緊急度設定の権限';permissionPanel.append(heading);for(const member of roster?.members.filter(m=>m.role!=='ADMIN')??[]){const label=document.createElement('label'),box=document.createElement('input');box.type='checkbox';box.checked=member.canTriage===true;box.disabled=busy;label.append(box,document.createTextNode(member.activityName));permissionPanel.append(label);box.onchange=async()=>{const allowed=box.checked;busy=true;await controls();try{await inFlight;await client.setMemberPermission(getGroup(),member.memberId,allowed);await refresh();status(english?'Permissions saved':'権限を保存しました');}catch(e){status(e.message);}finally{busy=false;await controls();}};}}}
  renderGroupPage();
 }
 let invitationMode=false,recoveryMode=false;const credentialDrafts={group:'',invitation:''};
 function credentials(){return $('group-join-identity').value.trim();}
 function credentialMode(invite){
  recoveryMode=false;setProperty(recoveryLabel,'hidden',true);$('group-recovery-code').required=false;setText($('group-next'),'次へ');setText(recoverLink,'以前の参加を復元する');
  invitationMode=invite;setProperty($('group-password-label'),'hidden',invite);$('group-join-password').required=!invite;
  $('group-join-password').disabled=invite;$('group-join-identity').maxLength=invite?256:80;
  setText($('group-identity-label'),invite?'招待コード':'グループ名');
  setText($('group-access-help'),invite?'管理者から届いた招待コードを貼り付けてください。グループ名・パスワードは不要です。':'管理者から案内されたグループ名と参加パスワードを入力してください。');
  setText($('group-join-method'),invite?'グループ名・パスワードで参加する':'招待コードで参加する');
  setText($('group-join-feedback'),'');setText($('cloud-registration-title'),invite?'招待コードで参加':'グループに参加');
 }
 function step(name){setProperty($('group-access'),'hidden',name);setProperty($('group-profile'),'hidden',!name);setText($('group-join-feedback'),'');setText($('cloud-registration-title'),name?'あなたの名前':'グループに参加');}
 $('group-join-method').onclick=()=>{credentialDrafts[invitationMode?'invitation':'group']=$('group-join-identity').value;credentialMode(!invitationMode);$('group-join-identity').value=credentialDrafts[invitationMode?'invitation':'group'];$('group-join-identity').focus();};
 $('group-back').onclick=()=>{step(false);$('group-join-identity').focus();};
 recoverLink.onclick=()=>{const next=!recoveryMode;credentialMode(false);if(!next)return;recoveryMode=true;joinHelp.open=false;setText($('cloud-registration-title'),'以前の参加を復元');setProperty($('group-password-label'),'hidden',true);$('group-join-password').required=false;$('group-join-password').disabled=true;recoveryLabel.hidden=false;$('group-recovery-code').required=true;setText($('group-access-help'),'以前と同じグループ名と、自分用の復元コードを入力してください。コードがなければ管理者に発行を依頼できます。');setText($('group-next'),'参加を復元');setText(recoverLink,'新しく参加する');};
 $('group-access').addEventListener('submit',event=>{if(!recoveryMode)return;event.preventDefault();event.stopImmediatePropagation();register('recover',{groupName:credentials(),code:$('group-recovery-code').value});},true);
 $('group-access').onsubmit=async e=>{e.preventDefault();if(busy)return;busy=true;await controls();try{await inFlight;const value=credentials();if(invitationMode){if(!/^[a-f0-9-]{36}:[a-f0-9]{64}$/i.test(value))throw Error('管理者から届いた招待コード全体を貼り付けてください。');setText($('group-joining-name'),'招待されたグループに参加します。');}else{const checked=await client.checkVenue(value,$('group-join-password').value);setText($('group-joining-name'),value+'：'+(checked.positionMode==='ACTUAL'?'位置情報を許可すると、現在地をグループに共有します。':'京都の割当位置で参加します。'));}step(true);$('group-join-name').focus();}catch(e){setText($('group-join-feedback'),e.message);}finally{busy=false;await controls();}};

 const createGroup=document.createElement('details');createGroup.id='group-create';createGroup.innerHTML='<summary>管理者としてグループを作成</summary><form id="group-create-form"><label>グループ名<input id="group-create-name" required maxlength="80" autocomplete="off"></label><label>あなたの名前<input id="group-create-person" required maxlength="60" autocomplete="nickname"></label><label>参加パスワード<input id="group-create-password" type="password" required maxlength="128" autocomplete="new-password"></label><button type="submit">グループを作成</button></form>';$('cloud-membership').append(createGroup);
 bindStaticUiLanguage(createGroup);
 $('group-create-form').onsubmit=e=>{e.preventDefault();const password=$('group-create-password').value;if(!password.trim()){setText($('group-join-feedback'),'参加パスワードを1〜128文字で入力してください。');return;}register('create',{groupName:$('group-create-name').value.trim(),activityName:$('group-create-person').value.trim(),password});};
 $('group-profile').onsubmit=e=>{e.preventDefault();const value=credentials(),split=value.indexOf(':'),activityName=$('group-join-name').value.trim();if(invitationMode)register('join',{groupId:value.slice(0,split),code:value.slice(split+1),activityName});else register('venue',{groupName:value,password:$('group-join-password').value,activityName});};
 $('venue-admin').onsubmit=async e=>{e.preventDefault();if(busy)return;if(!$('venue-initial-minutes').checkValidity()){setText(venueFeedback,document.documentElement.lang==='en'?'Enter 0–1440 whole minutes':'0〜1440分の整数で入力してください');$('venue-initial-minutes').focus();return;}busy=true;venueFeedback.textContent='';await controls();try{await inFlight;await client.startVenue(getGroup(),'',{positionMode:'KYOTO',presentationMode:$('venue-presentation-mode').value,elapsedMinutes:Number($('venue-initial-minutes').value)});await client.sync(getGroup());await refresh();tab?.('safety');status($('venue-presentation-mode').value==='EARTHQUAKE'?'地震の安否確認を開始しました。':'会場訓練を開始しました。');}catch(e){status(e.message);venueFeedback.textContent=e.message;}finally{busy=false;await controls();resetTimer();}};
 async function run(manual=false){
  if(busy||stopped||document.hidden)return;
  if(inFlight)return inFlight;
  const group=getGroup(),epoch=generation;if(!await client.session(group)){await controls();return;}
  if(manual)status('共有しています…','共有中');
  inFlight=(async()=>{
  try{const result=await client.sync(group);if(epoch!==generation||getGroup()!==group)return;await refresh({background:true});status(result.error?`端末保存済み・送信待ち ${result.pending}件：${result.error}`:`共有サーバーへ保存済み${result.pending?`・送信待ち ${result.pending}件`:''}${result.foreign?`・中継元の確認待ち ${result.foreign}件`:''}`,result.error||result.pending?'送信待ち':result.foreign?'確認待ち':'共有済み');setText($('receipts'),`この回のサーバー保存確認：${result.sent}件／取得：${result.received}件`);}catch(e){if(epoch===generation)status(!navigator.onLine||/fetch|network|load failed/i.test(e.message)?'接続を待っています。記録はこの端末に保存され、通信が戻ると再送します。':`端末の記録は保存済みです。再接続を待っています：${e.message}`,'接続待ち');}finally{inFlight=null;await controls();resetTimer();}})();return inFlight;
 }
 async function register(kind,input){
  if(busy)return;busy=true;const button=kind==='create'?$('group-create-form').querySelector('button'):kind==='recover'?$('group-next'):$('group-join'),label=button.textContent;
  button.disabled=true;button.setAttribute('aria-busy','true');setText(button,document.documentElement.lang==='en'?'Loading…':'読み込み中…');$('group-join-feedback').setAttribute('role','status');setText($('group-join-feedback'),document.documentElement.lang==='en'?'Joining the group…':'グループに参加しています…');
  await controls();status('グループに参加しています…','登録中');
  try{await inFlight;const session=await client.register(kind,input);generation++;stopped=false;await setGroup(session.groupId);window.dispatchEvent(new CustomEvent('group-change'));$('group').value=session.groupId;$('name').value=session.activityName;
   setText($('group-join-feedback'),document.documentElement.lang==='en'?'Loading safety check…':'安否確認を読み込み中…');
   for(const key of ['group-access','group-profile'])document.dispatchEvent(new CustomEvent('form-saved',{detail:{form:$(key)}}));
   // Registration already contains the roster. Pull the incident proof needed for
   // the first safety screen; do not wait for a second roster and upload pass.
   try{await client.pull(session);}catch{}
   await refresh();await onJoined?.();
   groupPage='overview';$('group-join-password').value='';$('group-create-password').value='';createGroup.open=false;$('cloud-membership').open=false;groupDetails.append($('cloud-membership'));step(false);
   status(kind==='recover'?'参加を復元しました。安否をもう一度回答してください。':'グループに参加しました。','参加済み');$('group-recovery-code').value='';
  }catch(e){status(e.message,'登録を確認');$('group-join-feedback').setAttribute('role','alert');setText($('group-join-feedback'),e.message);}finally{busy=false;button.removeAttribute('aria-busy');setText(button,label);await controls();run();}
 }
 $('cloud-sync').onclick=()=>{stopped=false;run(true);};
 $('group-open-safety').onclick=()=>tab?.('safety');
 $('venue-end').onclick=async()=>{if(busy||!confirm(document.documentElement.lang==='en'?'End this event? Records will be kept.':'今回の安否確認を終了しますか？ 記録は残ります。'))return;busy=true;await controls();try{await inFlight;await client.endVenue(getGroup());await refresh();status('安否確認を終了しました。次の安否確認を開始できます。');}catch(e){status(e.message);}finally{busy=false;await controls();resetTimer();}};
 async function manageAccess(action){if(busy)return;const session=await client.session(getGroup()),memberId=session.role==='ADMIN'?$('access-member').value:session.memberId;if(action==='revoke'&&!confirm('この参加登録の接続を停止します。会場の地図から外れますが、過去の記録は残ります。停止しますか？'))return;busy=true;await controls();try{await inFlight;const result=await client.access(getGroup(),action,memberId);if(action==='issue'){$('access-code').value=result.code;$('access-code-label').hidden=false;setText($('access-feedback'),'24時間以内に1回使えます。本人以外には渡さないでください。');}else{$('access-code-label').hidden=true;setText($('access-feedback'),'参加登録を停止しました。');await refresh();}}catch(e){setText($('access-feedback'),e.message);}finally{busy=false;await controls();}}
 $('access-issue').onclick=()=>manageAccess('issue');$('access-revoke').onclick=()=>manageAccess('revoke');$('access-member').onchange=()=>{$('access-code-label').hidden=true;setText($('access-feedback'),'');};
 $('cloud-invite').onclick=async()=>{if(busy)return;busy=true;await controls();try{await inFlight;const result=await client.invitation(getGroup());$('cloud-invite-output').value=`${getGroup()}:${result.code}`;setText($('cloud-invite-expiry'),`有効期限：${usDateTime(result.expiresAt)}`);setProperty($('cloud-invite-result'),'hidden',false);}catch(e){status(e.message);}finally{busy=false;await controls();}};
 for(const button of document.querySelectorAll('[data-stop]')){button.textContent='共有を停止';button.onclick=()=>{stopped=true;generation++;clearTimeout(timer);status('共有を停止しました。端末の記録は保持しています。','共有停止中');};}
 const visibility=()=>{clearTimeout(timer);if(!document.hidden&&!stopped)run();};
 document.addEventListener('visibilitychange',visibility);window.addEventListener('online',visibility);window.addEventListener('pageshow',visibility);
 window.addEventListener('pagehide',()=>clearTimeout(timer));
 for(const kind of ['join','venue']){const pending=await store.read('meta','cloud-registration:'+kind);if(pending){credentialMode(kind==='join');$('group-join-identity').value=kind==='join'?`${pending.input.groupId}:${pending.input.code}`:pending.input.groupName;$('group-join-name').value=pending.input.activityName;setText($('group-join-feedback'),'前回の参加は確認待ちです。通信を戻して、もう一度参加してください。');}}
 const savedSession=await client.session(getGroup());if(savedSession&&!$('name').value)$('name').value=await activityName(savedSession);
 $('cloud-membership').open=!savedSession;if(!savedSession)host.insertBefore($('cloud-membership'),overview);
 if(!savedSession)setText($('network'),'未参加');
 await controls();run();
 return {changed:()=>run(),refresh:controls,startTraining:async(title,area,presentationMode='DRILL')=>{if(busy)throw Error('共有が終わってから、もう一度開始してください');busy=true;try{await inFlight;const venue=await client.startVenue(getGroup(),'',{privateTraining:true,title,area,presentationMode});await client.sync(getGroup());return venue.incidentId;}finally{busy=false;await controls();resetTimer();}}};
}


