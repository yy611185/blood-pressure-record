// ============ Trend ============
function TrendScreen({ data }) {
  const [range, setRange] = useState(30);
  const [series, setSeries] = useState('both');
  const [hover, setHover] = useState(null);
  const wrapRef = useRef(null);

  const pts = useMemo(() => {
    const from = new Date(TODAY); from.setDate(from.getDate() - (range === 0 ? 999 : range - 1)); from.setHours(0, 0, 0, 0);
    const m = {};
    data.filter((r) => r.t >= from).forEach((r) => { (m[dayKey(r.t)] = m[dayKey(r.t)] || { t: r.t, rs: [] }).rs.push(r); });
    return Object.values(m).map((x) => ({ t: x.t, s: Math.round(x.rs.reduce((a, r) => a + r.s, 0) / x.rs.length), d: Math.round(x.rs.reduce((a, r) => a + r.d, 0) / x.rs.length), n: x.rs.length, rs: x.rs }));
  }, [data, range]);

  const W = 346, H = 220, PL = 30, PR = 8, PT = 12, PB = 24;
  const yMin = 50, yMax = 170;
  const x = (i) => PL + (pts.length === 1 ? (W - PL - PR) / 2 : i * (W - PL - PR) / (pts.length - 1));
  const y = (v) => PT + (yMax - v) / (yMax - yMin) * (H - PT - PB);
  const smooth = (key) => pts.map((p, i) => {
    if (i === 0) return `M${x(i)},${y(p[key])}`;
    const px = x(i - 1), py = y(pts[i - 1][key]), cx = (px + x(i)) / 2;
    return `C${cx},${py} ${cx},${y(p[key])} ${x(i)},${y(p[key])}`;
  }).join(' ');

  const onMove = (e) => {
    const r = wrapRef.current.getBoundingClientRect();
    const scale = r.width / W;
    const px = (e.clientX - r.left) / scale;
    let best = 0, bd = 1e9; pts.forEach((p, i) => { const dd = Math.abs(x(i) - px); if (dd < bd) { bd = dd; best = i; } });
    setHover(best);
  };
  const hp = pts[hover != null ? hover : pts.length - 1];
  const hg = hp && gradeOf(hp.s, hp.d);

  const all = pts.flatMap((p) => p.rs);
  const avgS = Math.round(all.reduce((a, r) => a + r.s, 0) / all.length);
  const avgD = Math.round(all.reduce((a, r) => a + r.d, 0) / all.length);
  const hi = all.reduce((a, r) => (r.s > a.s ? r : a), all[0]);
  const am = all.filter((r) => r.t.getHours() < 12), pm = all.filter((r) => r.t.getHours() >= 12);
  const mean = (a, k) => a.length ? Math.round(a.reduce((s, r) => s + r[k], 0) / a.length) : '—';
  const dist = ['ok', 'hn', 'g1', 'g2', 'g3', 'low'].map((k) => ({ k, n: all.filter((r) => gradeOf(r.s, r.d).key === k).length })).filter((x) => x.n);
  const okPct = Math.round((dist.filter((d) => d.k === 'ok' || d.k === 'hn').reduce((a, d) => a + d.n, 0)) / all.length * 100);

  const ticks = [60, 90, 120, 140, 160];
  const xLabels = pts.length ? [0, Math.floor((pts.length - 1) / 2), pts.length - 1] : [];

  return (
    <div className="screen screen-enter" data-screen-label="03 趋势">
      <div className="eyebrow">每日平均 · 真实时间轴</div>
      <h1 className="h1">趋势</h1>
      <div className="seg" style={{ marginTop: 16 }}>
        {[[7, '7 天'], [30, '30 天'], [0, '全部']].map(([v, l]) => <button key={v} className={range === v ? 'on' : ''} onClick={() => { setRange(v); setHover(null); }}>{l}</button>)}
      </div>

      <div className="card" style={{ marginTop: 14, padding: '14px 12px 12px' }}>
        <div className="tip">
          <div style={{ flex: 1 }}>
            <div className="d">{hp ? `${hp.t.getMonth() + 1}月${hp.t.getDate()}日 周${WD[hp.t.getDay()]} · ${hp.n} 次` : ''}</div>
            <div className="v num">{hp && <>{series !== 'd' && hp.s}{series === 'both' && <span style={{ opacity: .3, fontWeight: 300 }}>/</span>}{series !== 's' && hp.d}</>}</div>
          </div>
          <GradeChip g={hg} />
        </div>
        <div className="chart-wrap" ref={wrapRef} onPointerMove={onMove} onPointerDown={onMove} onPointerLeave={() => setHover(null)}>
          <svg width="100%" viewBox={`0 0 ${W} ${H}`}>
            {/* target band */}
            <rect x={PL} y={y(140)} width={W - PL - PR} height={y(90) - y(140)} fill="oklch(0.75 0.1 155 / .10)" rx="8" />
            <rect x={PL} y={y(90)} width={W - PL - PR} height={y(60) - y(90)} fill="oklch(0.75 0.1 245 / .08)" rx="8" />
            {ticks.map((t) => <g key={t}>
              <line x1={PL} x2={W - PR} y1={y(t)} y2={y(t)} stroke="var(--line)" strokeDasharray={t === 140 || t === 90 ? '0' : '3 4'} strokeWidth={t === 140 ? 1.5 : 1} />
              <text x={PL - 6} y={y(t) + 4} textAnchor="end" fontSize="10" fill="var(--ink3)" fontFamily="Bricolage Grotesque" fontWeight="600">{t}</text>
            </g>)}
            <text x={W - PR - 4} y={y(140) - 5} textAnchor="end" fontSize="9.5" fill="oklch(0.6 0.12 40)" fontWeight="700">140 高压警戒</text>
            {series !== 'd' && <path d={smooth('s')} fill="none" stroke="var(--accent)" strokeWidth="3" strokeLinecap="round" />}
            {series !== 's' && <path d={smooth('d')} fill="none" stroke="oklch(0.62 0.1 245)" strokeWidth="3" strokeLinecap="round" />}
            {pts.map((p, i) => <g key={i}>
              {series !== 'd' && <circle cx={x(i)} cy={y(p.s)} r={pts.length > 20 ? 2.4 : 3.6} fill="var(--card)" stroke="var(--accent)" strokeWidth="2" />}
              {series !== 's' && <circle cx={x(i)} cy={y(p.d)} r={pts.length > 20 ? 2.4 : 3.6} fill="var(--card)" stroke="oklch(0.62 0.1 245)" strokeWidth="2" />}
            </g>)}
            {hover != null && hp && <g>
              <line x1={x(hover)} x2={x(hover)} y1={PT} y2={H - PB} stroke="var(--ink)" strokeWidth="1.5" strokeDasharray="2 3" />
              {series !== 'd' && <circle cx={x(hover)} cy={y(hp.s)} r="6" fill="var(--accent)" stroke="var(--card)" strokeWidth="2.5" />}
              {series !== 's' && <circle cx={x(hover)} cy={y(hp.d)} r="6" fill="oklch(0.62 0.1 245)" stroke="var(--card)" strokeWidth="2.5" />}
            </g>}
            {xLabels.map((i, j) => <text key={j} x={x(i)} y={H - 6} textAnchor={j === 0 ? 'start' : j === 2 ? 'end' : 'middle'} fontSize="10" fill="var(--ink3)" fontFamily="Bricolage Grotesque" fontWeight="600">{pts[i].t.getMonth() + 1}/{pts[i].t.getDate()}</text>)}
          </svg>
        </div>
        <div style={{ display: 'flex', gap: 6, marginTop: 6 }}>
          {[['both', '双曲线'], ['s', '收缩压'], ['d', '舒张压']].map(([v, l]) => (
            <button key={v} className={'chip' + (series === v ? ' on' : '')} style={{ height: '2.2rem', fontSize: '.84rem' }} onClick={() => setSeries(v)}>
              {v !== 'd' && v !== 'both' && <i className="g-dot" style={{ background: 'var(--accent)' }} />}
              {v === 'd' && <i className="g-dot" style={{ background: 'oklch(0.62 0.1 245)' }} />}
              {l}
            </button>
          ))}
        </div>
      </div>

      <div className="sec-head"><h2 className="h2">这段时间</h2><span className="more">{all.length} 次测量</span></div>
      <div className="card">
        <div className="ring-stat">
          <div style={{ position: 'relative', width: 84, height: 84, flex: 'none' }}>
            <svg viewBox="0 0 42 42" width="84" height="84" style={{ transform: 'rotate(-90deg)' }}>
              <circle cx="21" cy="21" r="17" fill="none" stroke="var(--soft)" strokeWidth="6" />
              <circle cx="21" cy="21" r="17" fill="none" stroke="oklch(0.72 0.13 155)" strokeWidth="6" strokeLinecap="round" strokeDasharray={`${okPct * 1.068} 200`} style={{ transition: 'stroke-dasharray .6s' }} />
            </svg>
            <div className="num" style={{ position: 'absolute', inset: 0, display: 'grid', placeItems: 'center', fontWeight: 700, fontSize: '1.2rem' }}>{okPct}%</div>
          </div>
          <div>
            <div style={{ fontWeight: 800, fontSize: '1.05rem' }}>达标率</div>
            <div className="muted" style={{ fontSize: '.84rem', marginTop: 2, lineHeight: 1.5 }}>正常与正常高值占比。{okPct >= 60 ? '比上个周期更稳了 👏'.replace(' 👏', '，继续保持') : '多留意晨起血压'}</div>
          </div>
        </div>
        <div className="dist">{dist.map((d) => <i key={d.k} style={{ flex: d.n, background: `oklch(0.78 0.13 ${GRADES[d.k].h})` }} title={GRADES[d.k].name} />)}</div>
        <div className="legend" style={{ marginTop: 0 }}>{dist.map((d) => <span key={d.k}><i className="g-dot" style={{ '--h': GRADES[d.k].h }} />{GRADES[d.k].name} <b className="num">{d.n}</b></span>)}</div>
      </div>
      <div className="stat-grid" style={{ marginTop: 10 }}>
        <div className="stat"><div className="k">平均</div><div className="v num">{avgS}/{avgD}</div><div className="s">mmHg</div></div>
        <div className="stat"><div className="k">最高一次</div><div className="v num">{hi.s}/{hi.d}</div><div className="s">{hi.t.getMonth() + 1}月{hi.t.getDate()}日 {hm(hi.t)}</div></div>
        <div className="stat"><div className="k" style={{ display: 'flex', alignItems: 'center', gap: 4 }}><Icon n="sun" style={{ width: 14, height: 14 }} />早上平均</div><div className="v num">{mean(am, 's')}/{mean(am, 'd')}</div><div className="s">{am.length} 次</div></div>
        <div className="stat"><div className="k" style={{ display: 'flex', alignItems: 'center', gap: 4 }}><Icon n="moon" style={{ width: 14, height: 14 }} />晚上平均</div><div className="v num">{mean(pm, 's')}/{mean(pm, 'd')}</div><div className="s">{pm.length} 次</div></div>
      </div>
    </div>
  );
}
window.TrendScreen = TrendScreen;
