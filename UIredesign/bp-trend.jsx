// ============ Trend ============
// 修复：x 轴按真实日期定位（原稿按序号等距，却标注「真实时间轴」，缺测日被压缩）；
//      y 轴按数据自适应，超出不再溢出；无数据时空状态（原稿 NaN / 崩溃）；
//      去掉 90–140 绿带（对舒张压语义错误）→ 改为高压/低压两条目标线；
//      达标率按「我的」里的目标值计算；环比为真实对比
function TrendScreen({ data }) {
  const [range, setRange] = useState(30);
  const [series, setSeries] = useState('both');
  const [hover, setHover] = useState(null);
  const wrapRef = useRef(null);

  const window_ = useMemo(() => {
    const to = new Date(TODAY); to.setHours(23, 59, 59, 999);
    let from;
    if (range === 0) { from = data.length ? new Date(data[0].t) : new Date(TODAY); }
    else { from = new Date(TODAY); from.setDate(from.getDate() - (range - 1)); }
    from.setHours(0, 0, 0, 0);
    return { from, to };
  }, [data, range]);

  const pts = useMemo(() => {
    const m = {};
    data.filter((r) => r.t >= window_.from && r.t <= window_.to).forEach((r) => { (m[dayKey(r.t)] = m[dayKey(r.t)] || { t: r.t, rs: [] }).rs.push(r); });
    return Object.values(m).sort((a, b) => a.t - b.t).map((x) => {
      const day = new Date(x.t); day.setHours(12, 0, 0, 0);
      return { t: x.t, day, s: Math.round(x.rs.reduce((a, r) => a + r.s, 0) / x.rs.length), d: Math.round(x.rs.reduce((a, r) => a + r.d, 0) / x.rs.length), n: x.rs.length, rs: x.rs };
    });
  }, [data, window_]);

  // 上一个同长度周期（用于环比）
  const prevAll = useMemo(() => {
    if (range === 0) return [];
    const to = new Date(window_.from); const from = new Date(window_.from); from.setDate(from.getDate() - range);
    return data.filter((r) => r.t >= from && r.t < to);
  }, [data, range, window_]);

  const W = 346, H = 220, PL = 30, PR = 10, PT = 12, PB = 24;
  const vals = pts.flatMap((p) => [p.s, p.d]);
  const yMin = Math.min(50, ...vals.map((v) => Math.floor((v - 5) / 10) * 10));
  const yMax = Math.max(170, ...vals.map((v) => Math.ceil((v + 5) / 10) * 10));
  const t0 = new Date(window_.from); t0.setHours(12, 0, 0, 0);
  const t1 = new Date(TODAY); t1.setHours(12, 0, 0, 0);
  const span = Math.max(1, t1 - t0);
  const x = (p) => PL + ((p.day - t0) / span) * (W - PL - PR);
  const y = (v) => PT + (yMax - v) / (yMax - yMin) * (H - PT - PB);
  // 相邻点间隔 >2 天则断线，缺测不被连线「脑补」
  const path = (key) => pts.map((p, i) => {
    const gap = i > 0 && (p.day - pts[i - 1].day) / 864e5 > 2;
    if (i === 0 || gap) return `M${x(p)},${y(p[key])}`;
    const q = pts[i - 1], cx = (x(q) + x(p)) / 2;
    return `C${cx},${y(q[key])} ${cx},${y(p[key])} ${x(p)},${y(p[key])}`;
  }).join(' ');

  const onMove = (e) => {
    if (!pts.length) return;
    const r = wrapRef.current.getBoundingClientRect();
    const px = (e.clientX - r.left) / (r.width / W);
    let best = 0, bd = 1e9; pts.forEach((p, i) => { const dd = Math.abs(x(p) - px); if (dd < bd) { bd = dd; best = i; } });
    setHover(best);
  };
  const hp = pts.length ? pts[hover != null ? hover : pts.length - 1] : null;
  const hg = hp && gradeOf(hp.s, hp.d);

  const all = pts.flatMap((p) => p.rs);
  const mean = (a, k) => a.length ? Math.round(a.reduce((s, r) => s + r[k], 0) / a.length) : null;
  const avgS = mean(all, 's'), avgD = mean(all, 'd');
  const hi = all.length ? all.reduce((a, r) => (r.s > a.s ? r : a), all[0]) : null;
  const am = all.filter((r) => r.t.getHours() < 12), pm = all.filter((r) => r.t.getHours() >= 12);
  const dist = ['ok', 'hn', 'g1', 'g2', 'g3', 'low'].map((k) => ({ k, n: all.filter((r) => gradeOf(r.s, r.d).key === k).length })).filter((q) => q.n);
  const pct = (arr) => arr.length ? Math.round(arr.filter((r) => onTarget(r.s, r.d)).length / arr.length * 100) : null;
  const okPct = pct(all), prevPct = pct(prevAll);
  const riskN = all.filter((r) => containsHighRisk(r.groups, r)).length;

  const tickStep = yMax - yMin > 140 ? 40 : 20;
  const ticks = []; for (let v = Math.ceil(yMin / tickStep) * tickStep; v <= yMax; v += tickStep) ticks.push(v);
  const xTicks = [t0, new Date((+t0 + +t1) / 2), t1];
  const dotR = pts.length > 20 ? 2.4 : 3.6;
  const fmt = (v, fb = '—') => (v == null ? fb : v);

  let verdict = '';
  if (okPct != null) {
    if (prevPct == null) verdict = okPct >= 60 ? '整体不错，继续保持。' : '多留意晨起血压。';
    else if (okPct - prevPct >= 5) verdict = `比上个周期（${prevPct}%）更稳了，继续保持。`;
    else if (prevPct - okPct >= 5) verdict = `比上个周期（${prevPct}%）有所下降，留意作息和服药。`;
    else verdict = `和上个周期（${prevPct}%）差不多。`;
  }

  return (
    <div className="screen screen-enter" data-screen-label="03 趋势">
      <div className="eyebrow">每日平均 · 按日期排布</div>
      <h1 className="h1">趋势</h1>
      <div className="seg" style={{ marginTop: 16 }} role="tablist">
        {[[7, '7 天'], [30, '30 天'], [0, '全部']].map(([v, l]) => <button key={v} role="tab" aria-selected={range === v} className={range === v ? 'on' : ''} onClick={() => { setRange(v); setHover(null); }}>{l}</button>)}
      </div>

      {!pts.length ? (
        <div className="card" style={{ marginTop: 14, display: 'flex', alignItems: 'center', gap: 14 }}>
          <Buddy mood="sleepy" hue={60} size={0.5} sprout={false} />
          <div className="muted" style={{ fontSize: '.9rem' }}>这段时间还没有记录。连续记几天，这里就会画出你的趋势。</div>
        </div>
      ) : <>
        <div className="card" style={{ marginTop: 14, padding: '14px 12px 12px' }}>
          <div className="tip">
            <div style={{ flex: 1 }}>
              <div className="d">{hp ? `${mdw(hp.t)} · ${hp.n} 次平均` : ''}</div>
              <div className="v num">{hp && <>{series !== 'd' && hp.s}{series === 'both' && <span style={{ opacity: .3, fontWeight: 300 }}>/</span>}{series !== 's' && hp.d}</>}</div>
            </div>
            <GradeChip g={hg} />
          </div>
          <div className="chart-wrap" ref={wrapRef} onPointerMove={onMove} onPointerDown={onMove} onPointerLeave={() => setHover(null)}
            role="img" aria-label={`${pts.length} 天的每日平均血压曲线，平均 ${avgS}/${avgD}`}>
            <svg width="100%" viewBox={`0 0 ${W} ${H}`}>
              {ticks.map((t) => <g key={t}>
                <line x1={PL} x2={W - PR} y1={y(t)} y2={y(t)} stroke="var(--line)" strokeDasharray="3 4" />
                <text x={PL - 6} y={y(t) + 4} textAnchor="end" fontSize="10" fill="var(--ink3)" fontFamily="Bricolage Grotesque" fontWeight="600">{t}</text>
              </g>)}
              {series !== 'd' && <><line x1={PL} x2={W - PR} y1={y(TARGET.s)} y2={y(TARGET.s)} stroke="var(--accent)" strokeWidth="1.5" strokeDasharray="6 4" opacity=".7" />
                <text x={W - PR - 2} y={y(TARGET.s) - 5} textAnchor="end" fontSize="9.5" fill="var(--accent-ink)" fontWeight="700">高压目标 {TARGET.s}</text></>}
              {series !== 's' && <><line x1={PL} x2={W - PR} y1={y(TARGET.d)} y2={y(TARGET.d)} stroke="oklch(0.62 0.1 245)" strokeWidth="1.5" strokeDasharray="6 4" opacity=".7" />
                <text x={W - PR - 2} y={y(TARGET.d) - 5} textAnchor="end" fontSize="9.5" fill="oklch(0.5 0.1 245)" fontWeight="700">低压目标 {TARGET.d}</text></>}
              {series !== 'd' && <path d={path('s')} fill="none" stroke="var(--accent)" strokeWidth="3" strokeLinecap="round" />}
              {series !== 's' && <path d={path('d')} fill="none" stroke="oklch(0.62 0.1 245)" strokeWidth="3" strokeLinecap="round" />}
              {pts.map((p, i) => <g key={i}>
                {series !== 'd' && <circle cx={x(p)} cy={y(p.s)} r={dotR} fill="var(--card)" stroke="var(--accent)" strokeWidth="2" />}
                {series !== 's' && <circle cx={x(p)} cy={y(p.d)} r={dotR} fill="var(--card)" stroke="oklch(0.62 0.1 245)" strokeWidth="2" />}
              </g>)}
              {hover != null && hp && <g>
                <line x1={x(hp)} x2={x(hp)} y1={PT} y2={H - PB} stroke="var(--ink)" strokeWidth="1.5" strokeDasharray="2 3" />
                {series !== 'd' && <circle cx={x(hp)} cy={y(hp.s)} r="6" fill="var(--accent)" stroke="var(--card)" strokeWidth="2.5" />}
                {series !== 's' && <circle cx={x(hp)} cy={y(hp.d)} r="6" fill="oklch(0.62 0.1 245)" stroke="var(--card)" strokeWidth="2.5" />}
              </g>}
              {xTicks.map((t, j) => <text key={j} x={PL + ((t - t0) / span) * (W - PL - PR)} y={H - 6} textAnchor={j === 0 ? 'start' : j === 2 ? 'end' : 'middle'} fontSize="10" fill="var(--ink3)" fontFamily="Bricolage Grotesque" fontWeight="600">{t.getMonth() + 1}/{t.getDate()}</text>)}
            </svg>
          </div>
          <div style={{ display: 'flex', gap: 6, marginTop: 6 }}>
            {[['both', '双曲线'], ['s', '收缩压'], ['d', '舒张压']].map(([v, l]) => (
              <button key={v} className={'chip' + (series === v ? ' on' : '')} aria-pressed={series === v} style={{ height: '2.2rem', fontSize: '.84rem' }} onClick={() => setSeries(v)}>
                {v === 's' && <i className="g-dot" style={{ background: 'var(--accent)' }} />}
                {v === 'd' && <i className="g-dot" style={{ background: 'oklch(0.62 0.1 245)' }} />}
                {l}
              </button>
            ))}
          </div>
        </div>

        <div className="sec-head"><h2 className="h2">这段时间</h2><span className="more">{all.length} 次测量{riskN ? ` · ${riskN} 次高风险` : ''}</span></div>
        <div className="card">
          <div className="ring-stat">
            <div style={{ position: 'relative', width: 84, height: 84, flex: 'none' }}>
              <svg viewBox="0 0 42 42" width="84" height="84" style={{ transform: 'rotate(-90deg)' }} aria-hidden="true">
                <circle cx="21" cy="21" r="17" fill="none" stroke="var(--soft)" strokeWidth="6" />
                <circle cx="21" cy="21" r="17" fill="none" stroke="oklch(0.72 0.13 155)" strokeWidth="6" strokeLinecap="round" strokeDasharray={`${okPct * 1.068} 200`} style={{ transition: 'stroke-dasharray .6s' }} />
              </svg>
              <div className="num" style={{ position: 'absolute', inset: 0, display: 'grid', placeItems: 'center', fontWeight: 700, fontSize: '1.2rem' }}>{okPct}%</div>
            </div>
            <div>
              <div style={{ fontWeight: 800, fontSize: '1.05rem' }}>达标率</div>
              <div className="muted" style={{ fontSize: '.84rem', marginTop: 2, lineHeight: 1.5 }}>低于你的目标 <span className="num">{TARGET.s}/{TARGET.d}</span> 且不偏低的占比。{verdict}</div>
            </div>
          </div>
          <div className="dist">{dist.map((d) => <i key={d.k} style={{ flex: d.n, background: `oklch(0.78 0.13 ${GRADES[d.k].h})` }} title={GRADES[d.k].name} />)}</div>
          <div className="legend" style={{ marginTop: 0 }}>{dist.map((d) => <span key={d.k}><i className="g-dot" style={{ '--h': GRADES[d.k].h }} />{GRADES[d.k].name} <b className="num">{d.n}</b></span>)}</div>
        </div>
        <div className="stat-grid" style={{ marginTop: 10 }}>
          <div className="stat"><div className="k">平均</div><div className="v num">{avgS}/{avgD}</div><div className="s">mmHg</div></div>
          <div className="stat"><div className="k">最高一次</div><div className="v num">{hi.s}/{hi.d}</div><div className="s">{hi.t.getMonth() + 1}月{hi.t.getDate()}日 {hm(hi.t)}</div></div>
          <div className="stat"><div className="k" style={{ display: 'flex', alignItems: 'center', gap: 4 }}><Icon n="sun" style={{ width: 14, height: 14 }} />上午平均</div><div className="v num">{am.length ? `${mean(am, 's')}/${mean(am, 'd')}` : '—'}</div><div className="s">{am.length} 次 · 12 点前</div></div>
          <div className="stat"><div className="k" style={{ display: 'flex', alignItems: 'center', gap: 4 }}><Icon n="moon" style={{ width: 14, height: 14 }} />下午及晚上</div><div className="v num">{pm.length ? `${mean(pm, 's')}/${mean(pm, 'd')}` : '—'}</div><div className="s">{pm.length} 次 · 12 点后</div></div>
        </div>
      </>}
    </div>
  );
}
window.TrendScreen = TrendScreen;
