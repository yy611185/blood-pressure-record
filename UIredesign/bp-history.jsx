// ============ History: 心情日历 + 当天记录 ============
// 修复：月份范围由数据决定（原稿写死 8–9 月，7 月数据无法查看）；
//      图例补齐 3 级/偏低/高风险；记录可点开 → 详情（原始各组）/ 编辑 / 删除 + 撤销；
//      症状也显示；日历格有无障碍标签；达标按用户目标计算
function dayAvg(rs) {
  const s = Math.round(rs.reduce((a, r) => a + r.s, 0) / rs.length), d = Math.round(rs.reduce((a, r) => a + r.d, 0) / rs.length);
  return { s, d, g: gradeOf(s, d) };
}

function RecCard({ r, onOpen }) {
  const g = gradeOf(r.s, r.d);
  const risk = containsHighRisk(r.groups, r);
  const sym = (r.symptoms || []).filter((x) => x !== '无症状');
  return (
    <button className={'rec' + (risk ? ' is-risk' : '')} onClick={() => onOpen(r)} aria-label={`${hm(r.t)} ${r.s}/${r.d} ${g.name}${risk ? ' 高风险' : ''}，查看详情`}>
      <div>
        <div className="t num">{hm(r.t)}</div>
        <div className="sc">{r.scene}</div>
      </div>
      <div>
        <div className="v num">{r.s}<span style={{ opacity: .3, fontWeight: 300 }}>/</span>{r.d}</div>
        <div className="p">{r.p != null && <><Icon n="heart" style={{ width: 12, height: 12 }} /><span className="num">{r.p}</span> · </>}{r.groups.length} 组</div>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4 }}><GradeChip g={g} />{risk && <RiskChip />}</div>
      {(sym.length > 0 || r.tags.length > 0 || r.note) && <div className="tags">
        {sym.map((t) => <span key={t} className="sym">{t}</span>)}
        {r.tags.map((t) => <span key={t}>{t}</span>)}
        {r.note && <span className="nt">“{r.note}”</span>}
      </div>}
    </button>
  );
}

function RecDetail({ r, onClose, onEdit, onDelete }) {
  const g = gradeOf(r.s, r.d);
  const risk = containsHighRisk(r.groups, r);
  const [ask, setAsk] = useState(false);
  const discard = r.strategy === 'discardFirst' && r.groups.length >= 2;
  return (
    <PhonePortal>
    <div className="scrim sheet-scrim" onClick={onClose}>
      <div className="sheet" role="dialog" aria-label="记录详情" onClick={(e) => e.stopPropagation()} data-screen-label="07 记录详情">
        <div className="grab" />
        <div className="faint" style={{ fontSize: '.84rem', fontWeight: 600 }}>{r.t.getFullYear()}年{mdw(r.t)} · {hm(r.t)} · {r.scene}</div>
        <div style={{ display: 'flex', alignItems: 'flex-end', gap: 12, marginTop: 6 }}>
          <div className="reading num g-ink" style={{ '--h': g.h, fontSize: '3.2rem', fontWeight: 700, lineHeight: 1 }}>{r.s}<span style={{ opacity: .3, fontWeight: 300 }}>/</span>{r.d}</div>
          <div style={{ display: 'flex', gap: 6, paddingBottom: 6 }}><GradeChip g={g} />{risk && <RiskChip />}</div>
        </div>
        <div className="muted" style={{ fontSize: '.84rem', marginTop: 4 }}>{discard ? `弃用第 1 组，取其余 ${r.groups.length - 1} 组平均` : `${r.groups.length} 组全部平均`}{r.p != null ? ` · 脉搏 ${r.p}` : ''}</div>

        <div className="raw">
          <div className="raw-h"><span>组</span><span>高压</span><span>低压</span><span>脉搏</span></div>
          {r.groups.map((x, i) => (
            <div key={i} className={'raw-r' + (discard && i === 0 ? ' off' : '') + (isHighRisk(x.s, x.d) ? ' hot' : '')}>
              <span>第{i + 1}组{discard && i === 0 ? '（不计）' : ''}</span><b className="num">{x.s}</b><b className="num">{x.d}</b><b className="num">{x.p ?? '—'}</b>
            </div>
          ))}
        </div>
        {((r.symptoms || []).some((x) => x !== '无症状') || r.tags.length > 0 || r.note) && (
          <div className="tags" style={{ marginTop: 12, display: 'flex', gap: 6, flexWrap: 'wrap' }}>
            {(r.symptoms || []).filter((x) => x !== '无症状').map((t) => <span key={t} className="tag sym">{t}</span>)}
            {r.tags.map((t) => <span key={t} className="tag">{t}</span>)}
            {r.note && <span className="tag nt">“{r.note}”</span>}
          </div>
        )}
        <div className="dlg-a" style={{ marginTop: 18 }}>
          <button className="btn btn-ghost" onClick={() => setAsk(true)} style={{ color: 'oklch(0.55 0.17 28)' }}><Icon n="trash" style={{ width: 18, height: 18 }} />删除</button>
          <button className="btn btn-primary" onClick={() => onEdit(r)}><Icon n="edit" style={{ width: 18, height: 18 }} />编辑</button>
        </div>
        {ask && <Confirm title="删除这条记录？" body={`${mdw(r.t)} ${hm(r.t)} · ${r.s}/${r.d}。删除后可在提示条里撤销。`} ok="删除" danger onOk={() => onDelete(r)} onCancel={() => setAsk(false)} />}
      </div>
    </div>
    </PhonePortal>
  );
}

function HistoryScreen({ data, onEdit, onDelete }) {
  const [mode, setMode] = useState('cal');
  const minMonth = useMemo(() => { const t = data.length ? data[0].t : TODAY; return t.getFullYear() * 12 + t.getMonth(); }, [data]);
  const maxMonth = TODAY.getFullYear() * 12 + TODAY.getMonth();
  const [ym, setYm] = useState(maxMonth);
  const year = Math.floor(ym / 12), month = ym % 12;
  const [sel, setSel] = useState(new Date(TODAY));
  const [open, setOpen] = useState(null);
  const byDay = useMemo(() => {
    const m = {}; data.forEach((r) => { (m[dayKey(r.t)] = m[dayKey(r.t)] || []).push(r); }); return m;
  }, [data]);
  const first = new Date(year, month, 1);
  const lead = (first.getDay() + 6) % 7;
  const days = new Date(year, month + 1, 0).getDate();
  const cells = [...Array(lead).fill(null), ...Array.from({ length: days }, (_, i) => new Date(year, month, i + 1))];
  const selRecs = (byDay[dayKey(sel)] || []).slice().sort((a, b) => b.t - a.t);

  const monthDays = Object.entries(byDay).filter(([k]) => k.startsWith(year + '-' + month + '-'));
  const monthCount = monthDays.reduce((a, [, rs]) => a + rs.length, 0);
  const okDays = monthDays.filter(([, rs]) => { const a = dayAvg(rs); return onTarget(a.s, a.d); }).length;

  const recent = data.slice().sort((a, b) => b.t - a.t).slice(0, 20);
  const recentByDay = [];
  recent.forEach((r) => { const k = dayKey(r.t); let last = recentByDay[recentByDay.length - 1]; if (!last || last.k !== k) recentByDay.push(last = { k, t: r.t, rs: [] }); last.rs.push(r); });

  const go = (d) => setYm((v) => Math.max(minMonth, Math.min(maxMonth, v + d)));

  return (
    <div className="screen screen-enter" data-screen-label="02 历史">
      <div className="eyebrow">共 {data.length} 条记录</div>
      <h1 className="h1">历史</h1>
      <div className="seg" style={{ marginTop: 16 }} role="tablist">
        <button role="tab" aria-selected={mode === 'cal'} className={mode === 'cal' ? 'on' : ''} onClick={() => setMode('cal')}>日历</button>
        <button role="tab" aria-selected={mode === 'list'} className={mode === 'list' ? 'on' : ''} onClick={() => setMode('list')}>近期</button>
      </div>

      {mode === 'cal' ? <>
        <div className="card" style={{ marginTop: 14, padding: '14px 14px 16px' }}>
          <div className="cal-head">
            <button className="icon-btn" onClick={() => go(-1)} aria-label="上个月" disabled={ym <= minMonth}><Icon n="chevL" /></button>
            <div style={{ textAlign: 'center' }}>
              <div className="m num">{year}年{month + 1}月</div>
              <div className="faint" style={{ fontSize: '.74rem', fontWeight: 600 }}>{monthCount} 次测量 · {okDays} 天达标</div>
            </div>
            <button className="icon-btn" onClick={() => go(1)} aria-label="下个月" disabled={ym >= maxMonth}><Icon n="chev" /></button>
          </div>
          <div className="cal-grid">
            {['一', '二', '三', '四', '五', '六', '日'].map((w) => <div key={w} className="cal-wd">{w}</div>)}
            {cells.map((d, i) => {
              if (!d) return <div key={i} />;
              const rs = byDay[dayKey(d)];
              const fut = d > TODAY;
              const a = rs ? dayAvg(rs) : null;
              const risk = rs && rs.some((r) => containsHighRisk(r.groups, r));
              const cls = 'cal-d num' + (rs ? ' has' : fut ? ' fut' : ' none') + (sameDay(d, TODAY) ? ' today' : '') + (sameDay(d, sel) ? ' sel' : '');
              const label = `${d.getMonth() + 1}月${d.getDate()}日` + (rs ? `，${rs.length} 次，平均 ${a.s}/${a.d} ${a.g.name}${risk ? '，含高风险' : ''}` : fut ? '' : '，未测量');
              return (
                <button key={i} className={cls} style={a ? { '--h': a.g.h } : null} disabled={fut} onClick={() => setSel(d)} aria-label={label} aria-pressed={sameDay(d, sel)}>
                  {d.getDate()}
                  {rs && <span className="cnt">{rs.slice(0, 3).map((_, j) => <i key={j} />)}</span>}
                  {rs && rs.some((r) => r.note) && <span className="nt" />}
                  {risk && <span className="rk">!</span>}
                </button>
              );
            })}
          </div>
          <div className="legend">
            {['ok', 'hn', 'g1', 'g2', 'g3', 'low'].map((k) => <span key={k}><i className="g-dot" style={{ '--h': GRADES[k].h }} />{GRADES[k].name}</span>)}
            <span><i className="g-dot" style={{ background: 'var(--accent)' }} />有备注</span>
            <span><i className="rk-dot">!</i>高风险</span>
          </div>
        </div>
        <div className="day-h"><span>{mdw(sel)}</span><span className="faint">{selRecs.length ? selRecs.length + ' 条' : ''}</span></div>
        {selRecs.length ? selRecs.map((r) => <RecCard key={r.id} r={r} onOpen={setOpen} />) : (
          <div className="card" style={{ display: 'flex', alignItems: 'center', gap: 14, boxShadow: 'none', background: 'var(--soft)' }}>
            <Buddy mood="sleepy" hue={60} size={0.5} sprout={false} />
            <div className="muted" style={{ fontSize: '.9rem' }}>这天没有测量，小压睡了一整天～</div>
          </div>
        )}
      </> : <>
        {recentByDay.length === 0 && <div className="card" style={{ marginTop: 14 }}><div className="muted">还没有记录。点下方 + 记第一次吧。</div></div>}
        {recentByDay.map((grp) => (
          <div key={grp.k}>
            <div className="day-h"><span>{relDay(grp.t)} · 周{WD[grp.t.getDay()]}</span></div>
            {grp.rs.map((r) => <RecCard key={r.id} r={r} onOpen={setOpen} />)}
          </div>
        ))}
      </>}

      {open && <RecDetail r={open} onClose={() => setOpen(null)} onEdit={(r) => { setOpen(null); onEdit(r); }} onDelete={(r) => { setOpen(null); onDelete(r); }} />}
    </div>
  );
}
window.HistoryScreen = HistoryScreen;
