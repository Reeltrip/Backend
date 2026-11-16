'use strict';

// 토큰은 메모리에만 둬요. URL·DOM·웹 저장소·로그에 넣지 않아요.
let token = '';
let selected = null;
let tab = 'sources';
let userPage = 0;
let dataPage = 0;
let query = '';
let generation = 0;
let usersGeneration = 0;
let writing = false;
let editing = null;
const $ = (id) => document.getElementById(id);
const labels = { sources: '링크 분석 상태', places: '보관함 카드', trash: '카드 휴지통', plans: '계정이 만든 여행계획', demo: '시연 준비' };
const categories = { FOOD: '맛집', CAFE: '카페', STAY: '숙소', SIGHTSEEING: '관광지', ACTIVITY: '액티비티', SHOPPING: '쇼핑', TRANSPORT: '교통', TIP: '팁' };
const statuses = { DONE: '완료', FAILED: '실패', PENDING: '대기', ANALYZING: '분석 중', DRAFT: '작성 중', COMPLETED: '완성' };
const tableLabels = { source: '분석 요청', source_link: '분석 링크', place: '카드 (휴지통 포함)', place_key_point: '핵심 포인트', plan: '여행계획', plan_day: '여행 일자', plan_node: '확정 노드', plan_stay: '숙소 선택', plan_alternative: '플랜B', plan_day_area: '일자별 구역', plan_member: '계획 멤버 관계' };

function el(tag, text, className) {
  const node = document.createElement(tag);
  if (text !== undefined && text !== null) node.textContent = String(text);
  if (className) node.className = className;
  return node;
}

function notice(message, error = false) {
  $('notice').textContent = message;
  $('notice').classList.toggle('error', error);
  $('notice').hidden = !message;
}

function clearSession() {
  token = '';
  selected = null;
  editing = null;
  generation++;
  usersGeneration++;
  $('app').hidden = true;
  $('login-panel').hidden = false;
  $('logout').hidden = true;
  $('admin-name').textContent = '';
  $('users').replaceChildren();
  $('content').replaceChildren();
  $('plan-detail').replaceChildren();
  $('account-title').textContent = '';
  $('account-meta').textContent = '';
  $('choose-account').hidden = false;
  $('account-panel').hidden = true;
  $('edit-form').reset();
  $('password').value = '';
  document.querySelectorAll('dialog[open]').forEach((dialog) => dialog.close());
}

async function api(path, options = {}) {
  const requestToken = token;
  const headers = new Headers(options.headers);
  if (requestToken) headers.set('Authorization', `Bearer ${requestToken}`);
  if (options.body !== undefined) headers.set('Content-Type', 'application/json');
  const response = await fetch(path, { ...options, headers, credentials: 'omit', cache: 'no-store', signal: AbortSignal.timeout(30000) });
  const payload = response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok) {
    if (response.status === 401 && requestToken && token === requestToken) clearSession();
    throw new Error(payload?.message || `요청을 처리하지 못했어요. (${response.status})`);
  }
  return payload;
}

async function safely(action) {
  try { await action(); }
  catch (error) { notice(error.name === 'TimeoutError' ? '응답이 늦어요. 목록을 새로고침해 결과를 확인해 주세요.' : error.message, true); }
}

function button(text, action, danger = false) {
  const node = el('button', text, danger ? 'danger' : '');
  node.type = 'button';
  node.addEventListener('click', () => safely(action));
  return node;
}

function setWriting(value) {
  writing = value;
  document.querySelectorAll('[data-write]').forEach((node) => {
    node.disabled = value || (node.dataset.confirmation !== undefined
      && $('reset-confirmation')?.value !== node.dataset.confirmation);
  });
}

async function mutate(action, success) {
  if (writing) return;
  const requestToken = token;
  setWriting(true);
  try {
    await action();
    if (token !== requestToken) return;
    notice(success);
    await loadData();
  } finally { setWriting(false); }
}

function writeButton(text, action, danger = false) {
  const node = button(text, action, danger);
  node.dataset.write = '';
  node.disabled = writing;
  return node;
}

function base(user = selected) { return `/admin/api/users/${encodeURIComponent(user.userId)}`; }
function date(value) { return value ? new Date(value).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' }) : '—'; }
function empty(message) { return el('div', message, 'empty'); }
function status(value) { return el('span', statuses[value] || value, `status ${value}`); }

function link(url, text) {
  try {
    const parsed = new URL(url);
    if (!['https:', 'http:'].includes(parsed.protocol) || parsed.username || parsed.password) throw new Error();
    const node = el('a', text || url);
    node.href = parsed.href;
    node.target = '_blank';
    node.rel = 'noopener noreferrer';
    return node;
  } catch { return el('span', text || url || '—'); }
}

function table(headers, rows) {
  const wrap = el('div', undefined, 'table-wrap');
  const node = el('table');
  const head = el('tr');
  for (const title of headers) { const th = el('th', title); th.scope = 'col'; head.append(th); }
  const thead = el('thead');
  thead.append(head);
  const body = el('tbody');
  for (const row of rows) {
    const tr = el('tr');
    for (const value of row) {
      const td = el('td');
      td.append(value instanceof Node ? value : el('span', value ?? '—'));
      tr.append(td);
    }
    body.append(tr);
  }
  node.append(thead, body);
  wrap.append(node);
  return wrap;
}

function description(title, subtitle) {
  const node = el('div');
  node.append(el('strong', title), el('small', subtitle));
  return node;
}

async function loadUsers() {
  const current = ++usersGeneration;
  $('users').replaceChildren(empty('계정을 불러오고 있어요…'));
  const result = await api(`/admin/api/users?query=${encodeURIComponent(query)}&page=${userPage}`);
  if (current !== usersGeneration || !token) return;
  const nodes = result.users.map((user) => {
    const node = button('', async () => {
      selected = user;
      dataPage = 0;
      $('choose-account').hidden = true;
      $('account-panel').hidden = false;
      $('account-title').textContent = user.nickname;
      $('account-meta').textContent = `${user.email} · 회원 #${user.userId}`;
      $('demo-badge').textContent = user.demo ? '데모 계정' : '일반 계정';
      $('demo-badge').classList.toggle('demo', user.demo);
      $('demo-tab').disabled = !user.demo;
      if (!user.demo && tab === 'demo') tab = 'sources';
      document.querySelectorAll('.user').forEach((item) => item.classList.remove('selected'));
      node.classList.add('selected');
      notice('');
      await loadData();
    });
    node.className = 'user';
    node.classList.toggle('selected', selected?.userId === user.userId);
    node.append(el('strong', `${user.nickname}${user.demo ? ' · 데모' : ''}`), el('small', user.email), el('small', `회원 #${user.userId}`));
    return node;
  });
  $('users').replaceChildren(...(nodes.length ? nodes : [empty('일치하는 계정이 없어요.')]));
  $('users-page').textContent = `${userPage + 1} 페이지`;
  $('users-prev').disabled = userPage === 0;
  $('users-next').disabled = !result.hasNext;
}

async function loadData() {
  if (!selected || !token) return;
  const current = ++generation;
  const owner = { ...selected };
  const currentTab = tab;
  $('section-title').textContent = labels[tab];
  document.querySelectorAll('[data-tab]').forEach((node) => {
    if (node.dataset.tab === tab) node.setAttribute('aria-current', 'page');
    else node.removeAttribute('aria-current');
  });
  $('data-pager').hidden = tab === 'demo';
  $('content').replaceChildren(empty('데이터를 불러오고 있어요…'));
  if (tab === 'demo') { renderDemo(owner); return; }
  const path = tab === 'trash' ? 'places?trash=true&' : `${tab}?`;
  const result = await api(`${base(owner)}/${path}page=${dataPage}`);
  if (current !== generation) return;
  const rows = result[currentTab === 'sources' ? 'links' : currentTab === 'trash' ? 'places' : currentTab];
  let content;
  if (!rows.length) content = empty('아직 데이터가 없어요.');
  else if (currentTab === 'sources') content = renderSources(rows);
  else if (currentTab === 'plans') content = renderPlans(rows, owner);
  else content = renderPlaces(rows, owner, currentTab === 'trash');
  $('content').replaceChildren(content);
  $('data-page').textContent = `${dataPage + 1} 페이지`;
  $('data-prev').disabled = dataPage === 0;
  $('data-next').disabled = !result.hasNext;
}

function renderSources(rows) {
  return table(['분석 요청', '원래 링크', '처리 상태', '실패 이유', '최근 변경'], rows.map((row) => [
    description(row.title || '제목 없음', `분석 #${row.sourceId} · 링크 #${row.sourceLinkId}`),
    link(row.url, `${row.platform} 원문 열기`), status(row.status),
    description(row.failMessage || '—', row.failCode || ''), date(row.updatedAt),
  ]));
}

function renderPlaces(rows, owner, deleted) {
  return table(['카드', '주소·좌표', deleted ? '휴지통으로 이동' : '최근 변경', '관리'], rows.map((row) => {
    const actions = el('div', undefined, 'actions');
    if (deleted) actions.append(writeButton('복원', async () => {
      if (!confirm(`${owner.nickname}님의 “${row.name}” 카드를 보관함으로 복원할까요? 계획의 기존 자리는 복원되지 않아요.`)) return;
      await mutate(() => api(`${base(owner)}/places/${row.placeId}/restorations`, { method: 'POST' }), '카드를 보관함으로 복원했어요.');
    }));
    else actions.append(button('수정', () => openEditor(owner, row.placeId)), writeButton('휴지통', async () => {
      if (!confirm(`${owner.nickname}님의 “${row.name}” 카드를 휴지통으로 옮길까요? 이 카드를 쓰는 계획의 노드·플랜B·숙소 선택도 제거돼요.`)) return;
      await mutate(() => api(`${base(owner)}/places/${row.placeId}`, { method: 'DELETE' }), '카드를 휴지통으로 옮겼어요.');
    }, true));
    return [description(row.name, `#${row.placeId} · ${categories[row.category] || row.category}`),
      description(row.address || '주소 없음', `${row.latitude ?? '—'}, ${row.longitude ?? '—'}`), date(deleted ? row.deletedAt : row.updatedAt), actions];
  }));
}

async function openEditor(owner, id) {
  const current = generation;
  const card = await api(`${base(owner)}/places/${id}`);
  if (current !== generation) return;
  editing = { owner, id };
  $('edit-form').reset();
  for (const field of $('edit-form').elements) {
    if (!field.name) continue;
    field.value = field.name === 'keyPoints' ? (card.keyPoints || []).join('\n') : (card[field.name] ?? '');
  }
  $('edit-account').textContent = `${owner.nickname} · 회원 #${owner.userId} · 카드 #${id}`;
  $('edit-dialog').showModal();
}

function renderPlans(rows, owner) {
  return table(['계획', '여행 기간', '상태', '공유', '관리'], rows.map((row) => {
    const actions = el('div', undefined, 'actions');
    actions.append(button('상세', async () => {
      const current = generation;
      const detail = await api(`${base(owner)}/plans/${row.planId}`);
      if (current !== generation) return;
      renderPlanDetail(detail);
      $('plan-dialog').showModal();
    }), writeButton('삭제', async () => {
      if (!confirm(`${owner.nickname}님의 “${row.name}” 계획을 삭제할까요? 일자·노드·숙소·멤버 관계가 함께 삭제되며 복원할 수 없어요.`)) return;
      await mutate(() => api(`${base(owner)}/plans/${row.planId}`, { method: 'DELETE' }), '여행계획을 삭제했어요.');
    }, true));
    return [description(row.name, `#${row.planId} · ${row.city}`), `${row.startDate} ~ ${row.endDate}`, status(row.status),
      row.shareType === 'EDIT' ? '함께 편집' : row.shareType ? '읽기 전용' : '꺼짐', actions];
  }));
}

function renderPlanDetail(plan) {
  $('plan-title').textContent = plan.name;
  const nodes = [el('p', `${plan.city} · ${plan.startDate} ~ ${plan.endDate}`, 'muted')];
  if (plan.stays.length) nodes.push(el('p', `숙소: ${plan.stays.map((stay) => stay.name).join(', ')}`));
  for (const day of plan.days) {
    const section = el('section', undefined, 'day');
    section.append(el('h3', `${day.dayNumber}일차 · ${day.date}${day.isCompleted ? ' · 완성' : ''}`));
    if (day.areas.length) section.append(el('p', `구역: ${day.areas.map((area) => area.name).join(', ')}`));
    const list = el('ol');
    for (const node of day.nodes) {
      const item = el('li', node.name);
      if (node.alternatives.length) item.append(el('small', ` · 플랜B: ${node.alternatives.map((alt) => alt.name).join(', ')}`));
      list.append(item);
    }
    section.append(day.nodes.length ? list : el('p', '확정한 장소가 없어요.', 'muted'));
    nodes.push(section);
  }
  $('plan-detail').replaceChildren(...nodes);
}

function renderDemo(owner) {
  const seed = el('section', undefined, 'card');
  seed.append(el('h3', '샘플 여행 만들기'), el('p', '데이터가 없는 데모 계정에 장소 카드 2장과 하루 여행계획을 만들어요. 예시 이름·주소·그림을 사용하며 실제 장소 정보가 아니에요.'));
  seed.append(writeButton('샘플 만들기', async () => {
    if (!confirm(`회원 #${owner.userId} (${owner.email})에 시연용 카드를 만들까요?`)) return;
    await mutate(() => api(`${base(owner)}/samples`, { method: 'POST', body: JSON.stringify({ imageUrl: new URL('/admin/sample-place.svg', location.origin).href }) }), '샘플 카드 2장과 여행계획을 만들었어요.');
  }));
  const reset = el('section', undefined, 'card warning');
  reset.append(el('h3', '데모 데이터 초기화'), el('p', '선택한 데모 계정의 링크 분석·카드·소유 여행계획을 영구 삭제해요. 휴지통 카드도 포함돼요. 회원 계정·인증정보·다른 계정의 계획·Storage 사진 원본은 유지돼요.'));
  const previewArea = el('div');
  reset.append(button('초기화 대상 미리보기', async () => {
    const current = generation;
    const preview = await api(`${base(owner)}/reset-preview`);
    if (current !== generation) return;
    const counts = el('div', undefined, 'counts');
    for (const [name, count] of Object.entries(preview.counts)) {
      const cell = el('div', tableLabels[name] || name, 'count');
      cell.prepend(el('strong', count));
      counts.append(cell);
    }
    previewArea.replaceChildren(el('p', `초기화 대상: ${owner.email} · 회원 #${owner.userId}`), counts);
    if (!preview.allowed) {
      const blockers = el('ul', undefined, 'blockers');
      preview.blockers.forEach((message) => blockers.append(el('li', message)));
      previewArea.append(blockers);
      return;
    }
    const label = el('label', `확인하려면 “${preview.confirmation}”를 입력해 주세요.`, 'confirm-label');
    const input = el('input');
    input.id = 'reset-confirmation';
    input.autocomplete = 'off';
    input.maxLength = 50;
    label.append(input);
    const execute = writeButton('이 데모 계정 데이터 영구 삭제', async () => {
      if (input.value !== preview.confirmation) return;
      if (!confirm(`회원 #${owner.userId} (${owner.email})의 미리보기 데이터를 영구 삭제할까요? 되돌릴 수 없어요.`)) return;
      await mutate(() => api(`${base(owner)}/resets`, { method: 'POST', body: JSON.stringify({ fingerprint: preview.fingerprint, confirmation: input.value }) }), '선택한 데모 계정의 데이터를 초기화했어요.');
    }, true);
    execute.dataset.confirmation = preview.confirmation;
    execute.disabled = true;
    input.addEventListener('input', () => { execute.disabled = writing || input.value !== preview.confirmation; });
    previewArea.append(label, execute, el('p', '대상이 바뀌면 실행을 중단해요. 시연 계정의 다른 작업을 멈춘 뒤 진행해 주세요.', 'hint'));
  }), previewArea);
  $('content').replaceChildren(seed, reset);
}

$('login-form').addEventListener('submit', (event) => {
  event.preventDefault();
  safely(async () => {
    const submit = $('login-form').querySelector('button');
    submit.disabled = true;
    notice('');
    try {
      const result = await api('/auth/login', { method: 'POST', body: JSON.stringify({ email: $('email').value.trim(), password: $('password').value }) });
      token = result.accessToken;
      const admin = await api('/admin/api/me');
      $('admin-name').textContent = admin.nickname;
      $('login-panel').hidden = true;
      $('logout').hidden = false;
      $('app').hidden = false;
      userPage = 0;
      query = '';
      $('user-query').value = '';
      await loadUsers();
    } catch (error) { clearSession(); throw error; }
    finally { $('password').value = ''; submit.disabled = false; }
  });
});

$('logout').addEventListener('click', () => { clearSession(); notice('이 탭의 로그인 정보를 지웠어요.'); });
window.addEventListener('pagehide', clearSession);
document.querySelectorAll('[data-close]').forEach((node) => node.addEventListener('click', () => $(node.dataset.close).close()));
$('search-form').addEventListener('submit', (event) => {
  event.preventDefault(); query = $('user-query').value.trim(); userPage = 0; safely(loadUsers);
});
$('users-prev').addEventListener('click', () => { userPage = Math.max(0, userPage - 1); safely(loadUsers); });
$('users-next').addEventListener('click', () => { userPage++; safely(loadUsers); });
document.querySelectorAll('[data-tab]').forEach((node) => node.addEventListener('click', () => {
  if (!selected || (node.dataset.tab === 'demo' && !selected.demo)) return;
  tab = node.dataset.tab; dataPage = 0; safely(loadData);
}));
$('reload').addEventListener('click', () => safely(loadData));
$('data-prev').addEventListener('click', () => { dataPage = Math.max(0, dataPage - 1); safely(loadData); });
$('data-next').addEventListener('click', () => { dataPage++; safely(loadData); });
$('edit-form').addEventListener('submit', (event) => {
  event.preventDefault();
  safely(async () => {
    if (!editing || writing) return;
    const { owner, id } = editing;
    const patch = {};
    for (const field of $('edit-form').elements) {
      if (!field.name) continue;
      if (field.name === 'keyPoints') patch[field.name] = field.value.split('\n').map((line) => line.trim()).filter(Boolean);
      else if (field.type === 'number') patch[field.name] = field.value === '' ? null : field.valueAsNumber;
      else patch[field.name] = field.value.trim() || null;
    }
    if (patch.keyPoints.length > 3 || patch.keyPoints.some((point) => point.length > 100)) throw new Error('핵심 포인트는 각 100자 이내로 최대 3개 입력해 주세요.');
    await mutate(async () => {
      await api(`${base(owner)}/places/${id}`, { method: 'PATCH', body: JSON.stringify(patch) });
      $('edit-dialog').close();
    }, '카드 변경을 저장했어요.');
  });
});
