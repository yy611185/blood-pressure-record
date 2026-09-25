// ============ 血压记录 · shared: data, grading, icons, Buddy ============
// 规则全部对齐仓库 v1.8.4：
//   BloodPressureRules / MeasurementInputRules / AverageCalculator /
//   MeasurementTags / MeasurementTimestampValidator / CategoryPresentation
const { useState, useEffect, useRef, useMemo, useCallback } = React;

// ---- Grading (《中国高血压防治指南》成人诊室标准) ----
// name = 芯片短标签；full = 仓库 CategoryPresentation 的完整标签
const GRADES = {
  low:   { key: 'low',   name: '偏低',     full: '血压偏低',  h: 245, mood: 'sleepy',  say: '有点偏低哦，起身慢一点，多喝点水～' },
  ok:    { key: 'ok',    name: '正常',     full: '正常',      h: 155, mood: 'happy',   say: '漂亮！今天的血压很听话 ✓' },
  hn:    { key: 'hn',    name: '正常高值', full: '正常高值',  h: 100, mood: 'smile',   say: '还不错，稍微留意一下盐和睡眠。' },
  g1:    { key: 'g1',    name: '1级高血压', full: '1级高血压', h: 62,  mood: 'meh',     say: '有点偏高，放松一下，过会儿再测一次？' },
  g2:    { key: 'g2',    name: '2级高血压', full: '2级高血压', h: 38,  mood: 'worried', say: '偏高了，记得按时吃药，不舒服要联系医生。' },
  g3:    { key: 'g3',    name: '3级高血压', full: '3级高血压', h: 22,  mood: 'worried', say: '读数很高！请休息后复测，持续偏高请及时就医。' },
};
// 修复：先判升高分级，再判偏低（仓库「边界组合如 125/55 按升高一侧归类为正常高值」）
function gradeOf(s, d) {
  if (s == null || d == null) return null;
  if (s >= 180 || d >= 110) return GRADES.g3;
  if (s >= 160 || d >= 100) return GRADES.g2;
  if (s >= 140 || d >= 90) return GRADES.g1;
  if (s >= 120 || d >= 80) return GRADES.hn;
  if (s < 90 || d < 60) return GRADES.low;
  return GRADES.ok;
}
// 高风险提醒：独立于分级（≥180 和/或 ≥120），任一原始组或平均值触发
const HIGH_RISK = { s: 180, d: 120 };
const isHighRisk = (s, d) => s >= HIGH_RISK.s || d >= HIGH_RISK.d;
const containsHighRisk = (groups, avg) => groups.some((g) => isHighRisk(+g.s, +g.d)) || (avg ? isHighRisk(avg.s, avg.d) : false);
const HIGH_RISK_SAY = '这次有读数达到 180/120 以上。请先静坐休息后复测；如伴有胸痛、剧烈头痛、视物模糊、说话不清等，请立即就医或拨打 120。';

// ---- 录入规则（MeasurementInputRules） ----
const RULES = {
  minGroups: 2, uiMaxGroups: 10,
  s: [40, 300], d: [20, 200], p: [20, 250],
  // 「偏离常见范围」二次确认（SessionFormLogic.buildAbnormalMessage）
  common: { s: [70, 260], d: [40, 150], p: [40, 220] },
};
// 返回某组的错误文案；全空返回 'empty'
function groupError(g) {
  const S = g.s.trim(), D = g.d.trim(), P = g.p.trim();
  if (!S && !D && !P) return 'empty';
  if (!S || !D) return '请填写高压和低压';
  const s = +S, d = +D;
  if (s < RULES.s[0] || s > RULES.s[1]) return `高压须在 ${RULES.s[0]}–${RULES.s[1]} 之间`;
  if (d < RULES.d[0] || d > RULES.d[1]) return `低压须在 ${RULES.d[0]}–${RULES.d[1]} 之间`;
  if (d >= s) return '低压要小于高压，检查一下';
  if (P && (+P < RULES.p[0] || +P > RULES.p[1])) return `脉搏须在 ${RULES.p[0]}–${RULES.p[1]} 之间`;
  return null;
}
function abnormalMessage(groups) {
  const list = groups.map((g, i) => {
    const c = RULES.common, L = `第${i + 1}组`;
    if (+g.s < c.s[0] || +g.s > c.s[1]) return `${L}高压 ${g.s}`;
    if (+g.d < c.d[0] || +g.d > c.d[1]) return `${L}低压 ${g.d}`;
    if (g.p && (+g.p < c.p[0] || +g.p > c.p[1])) return `${L}脉搏 ${g.p}`;
    return null;
  }).filter(Boolean);
  return list.length ? list.join('、') : null;
}
// AverageCalculator：strategy 'all' | 'discardFirst'（仅 ≥2 组时弃第一组）
function averageOf(groups, strategy = 'all') {
  if (!groups.length) return null;
  const eff = strategy === 'discardFirst' && groups.length >= 2 ? groups.slice(1) : groups;
  const mean = (a) => Math.round(a.reduce((x, y) => x + y, 0) / a.length);
  const ps = eff.map((g) => g.p).filter((p) => p !== '' && p != null).map(Number);
  return { s: mean(eff.map((g) => +g.s)), d: mean(eff.map((g) => +g.d)), p: ps.length ? mean(ps) : null, n: eff.length };
}

// ---- 标签（MeasurementTags） ----
const SCENES = ['晨起', '上午', '下午', '晚上', '凌晨', '其他'];
const SYMPTOMS = ['无症状', '头痛', '头晕', '心悸', '胸闷或胸痛', '视物模糊', '其他'];
const FACTORS = ['已服降压药', '未服药', '饮酒后', '咖啡浓茶后', '饱餐后', '睡眠不足', '情绪紧张', '刚运动完', '吸烟后', '洗澡后', '憋尿'];
const DANGER_SYMPTOMS = ['胸闷或胸痛', '视物模糊'];
function defaultSceneFor(h) { return h >= 5 && h <= 8 ? '晨起' : h >= 9 && h <= 11 ? '上午' : h >= 12 && h <= 17 ? '下午' : h >= 18 ? '晚上' : '凌晨'; }

// ---- 用户目标（UserProfileEntity.targetSystolic/Diastolic） ----
const TARGET = { s: 135, d: 85 };
const onTarget = (s, d) => s < TARGET.s && d < TARGET.d && s >= 90 && d >= 60;

// ---- Sample data (deterministic) ----
function rng(seed) { let s = seed; return () => (s = (s * 16807) % 2147483647) / 2147483647; }
const TODAY = new Date(2026, 8, 25, 9, 12);
const TAGS = FACTORS;
function makeData() {
  const r = rng(42); const out = [];
  for (let back = 58; back >= 0; back--) {
    const day = new Date(TODAY); day.setDate(day.getDate() - back);
    const trend = back / 58 * 10; // slowly improving
    const plan = back === 0 ? [7] : (back > 12 && r() < 0.12 ? [] : r() < 0.6 ? [7, 21] : [r() < .5 ? 7 : 20]);
    plan.forEach((hr) => {
      const base = hr < 12 ? 134 : 126;
      const spike = r() < 0.08 ? 18 : 0;
      const groups = [0, 1].map(() => {
        const s = Math.round(base + trend + spike + (r() - .5) * 18);
        return { s, d: Math.round(s * 0.62 + (r() - .5) * 10), p: Math.round(66 + r() * 16) };
      });
      const avg = averageOf(groups);
      const t = new Date(day); t.setHours(hr, Math.floor(r() * 50) + 5);
      const tags = r() < 0.35 ? [FACTORS[Math.floor(r() * FACTORS.length)]] : [];
      const note = r() < 0.08 ? '早饭前测的' : '';
      out.push({ id: 'r' + out.length, t, s: avg.s, d: avg.d, p: avg.p, groups, strategy: 'all', scene: defaultSceneFor(hr), symptoms: ['无症状'], tags, note });
    });
  }
  return out;
}

const MEDS_INIT = [
  { id: 'm1', name: '苯磺酸氨氯地平', dose: '5mg · 1 片', time: '08:00', mh: 20, done: true },
  { id: 'm2', name: '缬沙坦胶囊', dose: '80mg · 1 粒', time: '20:00', mh: 250, done: false },
];

// ---- helpers ----
const pad2 = (n) => String(n).padStart(2, '0');
const hm = (t) => pad2(t.getHours()) + ':' + pad2(t.getMinutes());
const sameDay = (a, b) => a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
const WD = ['日', '一', '二', '三', '四', '五', '六'];
const dayKey = (t) => t.getFullYear() + '-' + t.getMonth() + '-' + t.getDate();
function greet(h) { return h < 6 ? '夜深了' : h < 11 ? '早上好' : h < 14 ? '中午好' : h < 18 ? '下午好' : '晚上好'; }
const mdw = (t) => `${t.getMonth() + 1}月${t.getDate()}日 星期${WD[t.getDay()]}`;
function relDay(t, now = TODAY) {
  const a = new Date(t); a.setHours(0, 0, 0, 0); const b = new Date(now); b.setHours(0, 0, 0, 0);
  const diff = Math.round((b - a) / 864e5);
  return diff === 0 ? '今天' : diff === 1 ? '昨天' : diff === 2 ? '前天' : `${t.getMonth() + 1}月${t.getDate()}日`;
}
// 连续记录天数：从今天（今天没测则从昨天）往回数
function streakOf(data, now = TODAY) {
  const days = new Set(data.map((r) => dayKey(r.t)));
  const d = new Date(now); if (!days.has(dayKey(d))) d.setDate(d.getDate() - 1);
  let n = 0; while (days.has(dayKey(d))) { n++; d.setDate(d.getDate() - 1); }
  return n;
}
const distinctDays = (data) => new Set(data.map((r) => dayKey(r.t))).size;
const toDateInput = (t) => `${t.getFullYear()}-${pad2(t.getMonth() + 1)}-${pad2(t.getDate())}`;

// ---- Icons (simple strokes) ----
const IC = {
  home: <><path d="M4 11.5 12 5l8 6.5V19a1 1 0 0 1-1 1h-4v-5h-6v5H5a1 1 0 0 1-1-1z"/></>,
  cal: <><rect x="4" y="5.5" width="16" height="14.5" rx="3"/><path d="M4 10h16M8.5 3.5v4M15.5 3.5v4"/></>,
  trend: <><path d="M4 17l5-5 4 3 7-8"/><path d="M15 7h5v5"/></>,
  me: <><circle cx="12" cy="8.5" r="3.8"/><path d="M4.5 20c1.2-4 4-5.5 7.5-5.5s6.3 1.5 7.5 5.5"/></>,
  plus: <><path d="M12 5v14M5 12h14"/></>,
  back: <><path d="M14.5 5.5 8 12l6.5 6.5"/></>,
  close: <><path d="M6 6l12 12M18 6 6 18"/></>,
  chev: <><path d="M9.5 6l6 6-6 6"/></>,
  chevL: <><path d="M14.5 6l-6 6 6 6"/></>,
  del: <><path d="M9 5h10a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H9l-6-7z"/><path d="M12 9.5l5 5M17 9.5l-5 5"/></>,
  heart: <><path d="M12 20s-7.5-4.6-7.5-10A4.3 4.3 0 0 1 12 7.3 4.3 4.3 0 0 1 19.5 10c0 5.4-7.5 10-7.5 10z"/></>,
  bell: <><path d="M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 1.5h-15z"/><path d="M10 20.5h4"/></>,
  pill: <><rect x="3.5" y="8.5" width="17" height="7" rx="3.5" transform="rotate(-35 12 12)"/><path d="M9.2 7.8l5.6 8.4" /></>,
  folder: <><path d="M3.5 7a1.5 1.5 0 0 1 1.5-1.5h4l2 2.5h8A1.5 1.5 0 0 1 20.5 9.5V17a1.5 1.5 0 0 1-1.5 1.5H5A1.5 1.5 0 0 1 3.5 17z"/></>,
  eye: <><path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z"/><circle cx="12" cy="12" r="3"/></>,
  info: <><circle cx="12" cy="12" r="8.5"/><path d="M12 11v5.5M12 7.8v.2"/></>,
  shield: <><path d="M12 3.5 19 6v5.5c0 4.5-3 7.5-7 9-4-1.5-7-4.5-7-9V6z"/><path d="M8.8 12l2.2 2.2 4.3-4.4"/></>,
  sun: <><circle cx="12" cy="12" r="4"/><path d="M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4"/></>,
  moon: <><path d="M19.5 14.5A7.5 7.5 0 0 1 9.5 4.5a7.5 7.5 0 1 0 10 10z"/></>,
  check: <><path d="M5 12.5l4.5 4.5L19 7.5"/></>,
  clock: <><circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/></>,
  text: <><path d="M5 6.5h14M12 6.5V19M8.5 19h7"/></>,
  export: <><path d="M12 15V4M7.5 8.5 12 4l4.5 4.5"/><path d="M5 13.5V19a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-5.5"/></>,
  lock: <><rect x="5" y="10.5" width="14" height="10" rx="2.5"/><path d="M8 10.5V8a4 4 0 0 1 8 0v2.5"/></>,
  widget: <><rect x="4" y="4" width="7" height="7" rx="2"/><rect x="13" y="4" width="7" height="7" rx="2"/><rect x="4" y="13" width="16" height="7" rx="2"/></>,
  pulse: <><path d="M3 12h4l2-5 4 10 2-5h6"/></>,
  flame: <><path d="M12 21c-3.9 0-6.5-2.6-6.5-6.2 0-3.4 2.6-5.3 3.8-8.3.4 1.8 1.4 3 2.6 3.6.3-2.6 1.6-4.8 3.6-6.6-.2 3 3 5.4 3 10 0 4.4-2.9 7.5-6.5 7.5z"/></>,
  alert: <><path d="M12 4 21 19.5H3z"/><path d="M12 10v4.5M12 17v.2"/></>,
  edit: <><path d="M5 19h3.5L19 8.5 15.5 5 5 15.5z"/><path d="M13.5 7l3.5 3.5"/></>,
  trash: <><path d="M5 7h14M10 7V5h4v2M7 7l1 12h8l1-12"/></>,
  avg: <><path d="M5 12h14M5 7h14M5 17h9"/></>,
};
function Icon({ n, sw = 2, style }) {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round" style={style} aria-hidden="true">{IC[n]}</svg>;
}

// ---- Buddy: 小压 — a squishy mascot whose mood follows the reading ----
function Buddy({ mood = 'idle', hue = 32, size = 1, sprout = true, onClick, label }) {
  const [jump, setJump] = useState(false);
  const tap = () => { setJump(false); requestAnimationFrame(() => setJump(true)); setTimeout(() => setJump(false), 720); onClick && onClick(); };
  return (
    <div className={'buddy m-' + mood + (jump ? ' jump' : '')} style={{ '--h': hue, fontSize: 10 * size + 'px' }} onClick={tap}
      role={onClick ? 'button' : 'img'} aria-label={label || '吉祥物小压'} tabIndex={onClick ? 0 : undefined}>
      <div className="b-shadow" />
      <div className="b-wrap">
        <div className="b-body" />
        {sprout && <div className="sprout" />}
        <div className="eye l" /><div className="eye r" />
        <div className="cheek l" /><div className="cheek r" />
        <div className="mouth" />
      </div>
    </div>
  );
}

function GradeChip({ g }) {
  if (!g) return null;
  return <span className="chip-grade g-soft g-ink" style={{ '--h': g.h }}><i className="g-dot" />{g.name}</span>;
}
function RiskChip() {
  return <span className="chip-grade risk-chip"><Icon n="alert" sw={2.4} style={{ width: 13, height: 13 }} />高风险</span>;
}

function StatusBar({ dark }) {
  return (
    <div className="statusbar" style={{ color: dark ? '#fff' : '#1c1612' }}>
      <span className="num">9:41</span>
      <div className="isl" />
      <div className="sb-r">
        <div className="sb-bars"><i style={{ height: 4 }} /><i style={{ height: 6 }} /><i style={{ height: 8 }} /><i style={{ height: 11 }} /></div>
        <div className="sb-batt"><i /></div>
      </div>
    </div>
  );
}

// 挂到手机框顶层，避免在滚动容器里定位错乱
function PhonePortal({ children }) {
  const host = document.querySelector('.phone');
  return host ? ReactDOM.createPortal(children, host) : children;
}

// 通用确认弹窗
function Confirm({ title, body, ok = '确定', cancel = '取消', danger, onOk, onCancel }) {
  return (
    <PhonePortal>
    <div className="scrim" onClick={onCancel}>
      <div className="dialog" role="alertdialog" aria-label={title} onClick={(e) => e.stopPropagation()}>
        <div className="dlg-t">{title}</div>
        {body && <div className="dlg-b">{body}</div>}
        <div className="dlg-a">
          <button className="btn btn-ghost" onClick={onCancel}>{cancel}</button>
          <button className={'btn ' + (danger ? 'btn-danger' : 'btn-primary')} onClick={onOk}>{ok}</button>
        </div>
      </div>
    </div>
    </PhonePortal>
  );
}

Object.assign(window, {
  GRADES, gradeOf, HIGH_RISK, isHighRisk, containsHighRisk, HIGH_RISK_SAY, RULES, groupError, abnormalMessage, averageOf,
  SCENES, SYMPTOMS, FACTORS, DANGER_SYMPTOMS, defaultSceneFor, TARGET, onTarget,
  makeData, MEDS_INIT, TODAY, TAGS, pad2, hm, sameDay, WD, dayKey, greet, mdw, relDay, streakOf, distinctDays, toDateInput,
  Icon, Buddy, GradeChip, RiskChip, StatusBar, Confirm, PhonePortal,
});
