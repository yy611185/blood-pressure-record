// ============ History: 心情日历 + 当天记录 ============
function RecCard({ r }) {
  const g = gradeOf(r.s, r.d);
  return (
    <div className="rec">
      <div>
        <div className="t num">{hm(r.t)}</div>
        <div className="sc">{r.scene}</div>
      </div>
      <div>
        <div className="v num">{r.s}<span style={{ opacity: .3, fontWeight: 300 }}>/</span>{r.d}</div>
        <div className="p"><Icon n="heart" style={{ width: 12, height: 12 }} /><span className="num">{r.p}</span> · {r.groups.length} 组平均</div>
      </div>
      <GradeChip g={g} />
      {(r.tags.length > 0 || r.note) && <div className="tags">{r.tags.map((t) => <span key={t}>{t}</span>)}{r.note && <span style={{ background: 'var(--accent-soft)', color: 'var(--accent-ink)' }}>“{r.note}”</span>}</div>}
    </div>
  );
}

function HistoryScreen({ data }) {
  const [mode, setMode] = useState('cal');
  const [month, setMonth] = useState(8);
  const [sel, setSel] = useState(new Date(TODAY));
  const byDay = useMemo(() => {
    const m = {}; data.forEach((r) => { (m[dayKey(r.t)] = m[dayKey(r.t)] || []).push(r); }); return m;
  }, [data]);
  const first = new Date(2026, month, 1);
  const lead = (first.getDay() + 6) % 7;
  const days = new Date(2026, month + 1, 0).getDate();
  const cells = [...Array(lead).fill(null), ...Array.from({ length: days }, (_, i) => new Date(2026, month, i + 1))];
  const selRecs = (byDay[dayKey(sel)] || []).slice().reverse();

  const monthRecs = data.filter((r) => r.t.getMonth() === month);
  const okDays = Object.entries(byDay).filter(([k, rs]) => k.startsWith('2026-' + month + '-')).filter(([, rs]) => { const s = rs.reduce((a, r) => a + r.s, 0) / rs.length, d = rs.reduce((a, r) => a + r.d, 0) / rs.length; const g = gradeOf(s, d); return g.key === 'ok' || g.key === 'hn'; }).length;

  const recent = data.slice(-14).reverse();
  const recentByDay = [];
  recent.forEach((r) => { const k = dayKey(r.t); let last = recentByDay[recentByDay.length - 1]; if (!last || last.k !== k) recentByDay.push(last = { k, t: r.t, rs: [] }); last.rs.push(r); });

  return (
    <div className="screen screen-enter" data-screen-label="02 历史">
      <div className="eyebrow">共 {data.length} 条记录</div>
      <h1 className="h1">历史</h1>
      <div className="seg" style={{ marginTop: 16 }}>
        <button className={mode === 'cal' ? 'on' : ''} onClick={() => setMode('cal')}>日历</button>
        <button className={mode === 'list' ? 'on' : ''} onClick={() => setMode('list')}>近期</button>
      </div>

      {mode === 'cal' ? <>
        <div className="card" style={{ marginTop: 14, padding: '14px 14px 16px' }}>
          <div className="cal-head">
            <button className="icon-btn" onClick={() => setMonth((m) => Math.max(7, m - 1))} aria-label="上个月" style={{ opacity: month === 7 ? .35 : 1 }}><Icon n="chevL" /></button>
            <div style={{ textAlign: 'center' }}>
              <div className="m num">2026年{month + 1}月</div>
              <div className="faint" style={{ fontSize: '.74rem', fontWeight: 600 }}>{monthRecs.length} 次测量 · {okDays} 天达标</div>
            </div>
            <button className="icon-btn" onClick={() => setMonth((m) => Math.min(8, m + 1))} aria-label="下个月" style={{ opacity: month === 8 ? .35 : 1 }}><Icon n="chev" /></button>
          </div>
          <div className="cal-grid">
            {['一', '二', '三', '四', '五', '六', '日'].map((w) => <div key={w} className="cal-wd">{w}</div>)}
            {cells.map((d, i) => {
              if (!d) return <div key={i} />;
              const rs = byDay[dayKey(d)];
              const fut = d > TODAY;
              let g = null;
              if (rs) { const s = rs.reduce((a, r) => a + r.s, 0) / rs.length, dd = rs.reduce((a, r) => a + r.d, 0) / rs.length; g = gradeOf(s, dd); }
              const cls = 'cal-d num' + (rs ? ' has' : fut ? ' fut' : ' none') + (sameDay(d, TODAY) ? ' today' : '') + (sameDay(d, sel) ? ' sel' : '');
              return (
                <button key={i} className={cls} style={g ? { '--h': g.h } : null} disabled={fut} onClick={() => setSel(d)}>
                  {d.getDate()}
                  {rs && <span className="cnt">{rs.map((_, j) => <i key={j} />)}</span>}
                  {rs && rs.some((r) => r.note) && <span className="nt" />}
                </button>
              );
            })}
          </div>
          <div className="legend">
            {['ok', 'hn', 'g1', 'g2'].map((k) => <span key={k}><i className="g-dot" style={{ '--h': GRADES[k].h }} />{GRADES[k].name}</span>)}
            <span><i className="g-dot" style={{ background: 'var(--accent)' }} />有备注</span>
          </div>
        </div>
        <div className="day-h"><span>{sel.getMonth() + 1}月{sel.getDate()}日 周{WD[sel.getDay()]}</span><span className="faint">{selRecs.length ? selRecs.length + ' 条' : ''}</span></div>
        {selRecs.length ? selRecs.map((r) => <RecCard key={r.id} r={r} />) : (
          <div className="card" style={{ display: 'flex', alignItems: 'center', gap: 14, boxShadow: 'none', background: 'var(--soft)' }}>
            <Buddy mood="sleepy" hue={60} size={0.5} sprout={false} />
            <div className="muted" style={{ fontSize: '.9rem' }}>这天没有测量，小压睡了一整天～</div>
          </div>
        )}
      </> : <>
        {recentByDay.map((grp) => (
          <div key={grp.k}>
            <div className="day-h"><span>{sameDay(grp.t, TODAY) ? '今天' : (grp.t.getMonth() + 1) + '月' + grp.t.getDate() + '日'} · 周{WD[grp.t.getDay()]}</span></div>
            {grp.rs.map((r) => <RecCard key={r.id} r={r} />)}
          </div>
        ))}
      </>}
    </div>
  );
}
window.HistoryScreen = HistoryScreen;
