// ============ Record flow: 静坐 → 录入 → 情况 → 完成 ============
const SYMPTOMS = ['没有，挺好的', '头痛', '头晕', '心悸', '胸闷或胸痛', '视物模糊', '其他'];
const FACTORS = ['已服降压药', '未服药', '饮酒后', '咖啡浓茶后', '饱餐后', '睡眠不足', '情绪紧张', '刚运动完', '吸烟后', '洗澡后', '憋尿'];
const FIELDS = [{ k: 's', name: '收缩压', sub: '高压', max: 3 }, { k: 'd', name: '舒张压', sub: '低压', max: 3 }, { k: 'p', name: '脉搏', sub: '选填', max: 3 }];

function RecordFlow({ onClose, onSave, buddyOn, skipRest, last }) {
  const [step, setStep] = useState(skipRest ? 1 : 0);
  const [out, setOut] = useState(false);
  const [groups, setGroups] = useState([{ s: '', d: '', p: '' }, { s: '', d: '', p: '' }]);
  const [gi, setGi] = useState(0);
  const [fi, setFi] = useState(0);
  const [scene, setScene] = useState('上午');
  const [sym, setSym] = useState(['没有，挺好的']);
  const [fac, setFac] = useState([]);
  const [note, setNote] = useState('');
  const [sec, setSec] = useState(60);

  useEffect(() => {
    if (step !== 0) return;
    const t = setInterval(() => setSec((s) => (s <= 1 ? (clearInterval(t), 0) : s - 1)), 1000);
    return () => clearInterval(t);
  }, [step]);

  const close = () => { setOut(true); setTimeout(onClose, 280); };
  const valid = (g) => +g.s >= 50 && +g.s <= 260 && +g.d >= 30 && +g.d <= 180;
  const filled = groups.filter(valid);
  const avg = filled.length ? {
    s: Math.round(filled.reduce((a, g) => a + +g.s, 0) / filled.length),
    d: Math.round(filled.reduce((a, g) => a + +g.d, 0) / filled.length),
    p: filled.some((g) => g.p) ? Math.round(filled.filter((g) => g.p).reduce((a, g) => a + +g.p, 0) / filled.filter((g) => g.p).length) : null,
  } : null;
  const liveG = avg ? gradeOf(avg.s, avg.d) : null;

  const press = (k) => {
    setGroups((gs) => {
      const n = gs.map((g) => ({ ...g }));
      const f = FIELDS[fi].k; let v = n[gi][f];
      if (k === 'del') v = v.slice(0, -1);
      else if (v.length < 3) v = v + k;
      n[gi][f] = v;
      // auto advance: 3 digits, or 2 digits that can't take a third
      const auto = k !== 'del' && (v.length === 3 || (v.length === 2 && +v > 26 && f !== 's'));
      if (auto) setTimeout(() => {
        if (fi < 2) setFi(fi + 1);
        else if (gi < n.length - 1) { setGi(gi + 1); setFi(0); }
      }, 120);
      return n;
    });
  };
  const next = () => {
    if (fi < 2) setFi(fi + 1);
    else if (gi < groups.length - 1) { setGi(gi + 1); setFi(0); }
  };
  const addGroup = () => { setGroups((gs) => [...gs, { s: '', d: '', p: '' }]); setGi(groups.length); setFi(0); };
  const toggle = (arr, set, v, single) => set(arr.includes(v) ? arr.filter((x) => x !== v) : single ? [v] : [...arr.filter((x) => x !== '没有，挺好的'), v]);

  const save = () => { setStep(3); onSave({ ...avg, scene, tags: fac, note, groups: filled }); };
  const cur = groups[gi];

  return (
    <div className={'flow' + (out ? ' out' : '')} data-screen-label="05 记一次血压">
      <div className="flow-head">
        {step === 2 || (step === 1 && !skipRest)
          ? <button className="icon-btn" onClick={() => setStep(step - 1)} aria-label="上一步"><Icon n="back" /></button>
          : <button className="icon-btn" onClick={close} aria-label="关闭"><Icon n="close" /></button>}
        <div className="ttl">{['先静坐一会儿', '记一次血压', '测的时候怎么样？', '记好啦'][step]}</div>
        <div className="steps">{[0, 1, 2, 3].map((i) => <i key={i} className={i === step ? 'on' : ''} />)}</div>
      </div>

      {step === 0 && (
        <div className="flow-body">
          <div className="rest">
            <div className="breath"><div className="ring" /><div className="ring r2" /><div className="ring r3" /><div className="lbl">跟着呼吸</div></div>
            <div className="timer num">0:{pad2(sec)}</div>
            <div className="muted" style={{ fontSize: '.95rem', lineHeight: 1.7, maxWidth: 280 }}>坐直、双脚平放、手臂与心脏同高。<br />圈变大时吸气，变小时呼气。</div>
          </div>
          <button className="btn btn-primary btn-block" onClick={() => setStep(1)}>{sec === 0 ? '准备好了，开始记录' : '我已经静坐过了'}</button>
        </div>
      )}

      {step === 1 && (
        <div className="flow-body" style={{ paddingBottom: 24 }}>
          <div className="pad-top">
            <div className="gtabs">
              {groups.map((g, i) => <button key={i} className={'gtab' + (i === gi ? ' on' : '') + (valid(g) ? ' ok' : '')} onClick={() => { setGi(i); setFi(0); }}>第{i + 1}组</button>)}
              {groups.length < 4 && <button className="gtab add" onClick={addGroup}><Icon n="plus" style={{ width: 14, height: 14 }} />加一组</button>}
            </div>
          </div>
          <div className="cells">
            {FIELDS.map((f, i) => (
              <button key={f.k} className={'cell' + (i === 0 ? ' big' : '') + (i === fi ? ' on' : '')} onClick={() => setFi(i)}>
                <div>
                  <div className="k">{f.name}<small>{f.sub}</small></div>
                  <div className="val num">
                    {cur[f.k] ? cur[f.k] : <span className="ph">{i === 0 ? (last ? last.s : '120') : i === 1 ? (last ? last.d : '80') : '72'}</span>}
                    {i === fi && <span className="caret" />}
                  </div>
                </div>
                {i === 0 && liveG && <div style={{ marginBottom: 12 }}><GradeChip g={liveG} /></div>}
              </button>
            ))}
          </div>
          <div className="avg-strip">
            {avg ? <>
              <span className="muted" style={{ fontWeight: 600 }}>{filled.length} 组平均</span>
              <b className="num" style={{ fontSize: '1.1rem' }}>{avg.s}/{avg.d}</b>
              {avg.p && <span className="faint num">♥ {avg.p}</span>}
              <span style={{ marginLeft: 'auto' }} className="faint">自动计算</span>
            </> : <span className="faint">建议连续测两次，间隔 1–2 分钟，取平均更准</span>}
          </div>
          <div className="keypad">
            {['1', '2', '3', '4', '5', '6', '7', '8', '9'].map((k) => <button key={k} className="key num" onClick={() => press(k)}>{k}</button>)}
            <button className="key fn" onClick={() => press('del')} aria-label="删除"><Icon n="del" /></button>
            <button className="key num" onClick={() => press('0')}>0</button>
            {filled.length >= 1 && (fi === 2 && gi === groups.length - 1 || filled.length === groups.length)
              ? <button className="key fn" style={{ background: 'var(--accent)', color: 'var(--on-accent)' }} onClick={() => setStep(2)}>下一步</button>
              : <button className="key fn" onClick={next}>下一项</button>}
          </div>
        </div>
      )}

      {step === 2 && (
        <div className="flow-body">
          <div className="lbl-q">什么时候测的？</div>
          <div className="when">
            <div><Icon n="cal" /><span className="num">2026年9月25日</span></div>
            <div style={{ flex: '0 0 110px' }}><Icon n="clock" /><span className="num">{hm(TODAY)}</span></div>
          </div>
          <div className="chips" style={{ marginTop: 10 }}>{SCENES.map((s) => <button key={s} className={'chip' + (scene === s ? ' on' : '')} onClick={() => setScene(s)}>{s}</button>)}</div>
          <div className="lbl-q">有没有不舒服？</div>
          <div className="chips">{SYMPTOMS.map((s) => <button key={s} className={'chip' + (sym.includes(s) ? ' on' : '')} onClick={() => s === '没有，挺好的' ? setSym([s]) : toggle(sym, setSym, s)}>{s}</button>)}</div>
          <div className="lbl-q">可能影响血压的情况<small>可多选</small></div>
          <div className="chips">{FACTORS.map((s) => <button key={s} className={'chip' + (fac.includes(s) ? ' on' : '')} onClick={() => toggle(fac, setFac, s)}>{s}</button>)}</div>
          <div className="lbl-q">备注<small>选填</small></div>
          <textarea className="note" placeholder="比如「早饭前测的」" value={note} onChange={(e) => setNote(e.target.value)} />
          <div style={{ height: 16, flex: 'none' }} />
          <button className="btn btn-primary btn-block" style={{ flex: 'none' }} onClick={save}>保存这次记录 · <span className="num">{avg.s}/{avg.d}</span></button>
        </div>
      )}

      {step === 3 && avg && <DoneStep avg={avg} g={liveG} last={last} onClose={close} buddyOn={buddyOn} />}
    </div>
  );
}

function DoneStep({ avg, g, last, onClose, buddyOn }) {
  const pieces = useMemo(() => Array.from({ length: 36 }, (_, i) => ({
    left: Math.random() * 100, delay: Math.random() * .6, h: [32, 155, 250, 90, 340][i % 5], rot: Math.random() * 360, w: 6 + Math.random() * 8,
  })), []);
  const good = g.key === 'ok' || g.key === 'hn';
  const ds = last ? avg.s - last.s : 0;
  return (
    <div className="flow-body">
      {good && <div className="confetti">{pieces.map((p, i) => <i key={i} style={{ left: p.left + '%', animationDelay: p.delay + 's', background: `oklch(0.78 0.14 ${p.h})`, width: p.w, transform: `rotate(${p.rot}deg)` }} />)}</div>}
      <div className="done">
        {buddyOn ? <Buddy mood={g.mood} hue={g.h} size={1.3} /> : <div className="g-mid" style={{ '--h': g.h, width: 96, height: 96, borderRadius: 32, display: 'grid', placeItems: 'center', color: '#fff' }}><Icon n="check" sw={3} style={{ width: 44, height: 44 }} /></div>}
        <div className="reading num g-ink" style={{ '--h': g.h }}>{avg.s}<span style={{ opacity: .3, fontWeight: 300 }}>/</span>{avg.d}</div>
        <div style={{ marginTop: 10 }}><GradeChip g={g} /></div>
        <div className="bubble" style={{ marginTop: 18, borderRadius: 18, maxWidth: 300 }}>{g.say}</div>
        {last && (
          <div className="compare">
            <div><span>和上次比</span><b className="num" style={{ color: ds > 0 ? 'oklch(0.6 0.15 35)' : 'oklch(0.55 0.12 155)' }}>{ds > 0 ? '↑' : ds < 0 ? '↓' : '='} {Math.abs(ds)}</b></div>
            <div><span>脉搏</span><b className="num">{avg.p || '—'}</b></div>
            <div><span>连续记录</span><b className="num">13 天</b></div>
          </div>
        )}
      </div>
      <button className="btn btn-primary btn-block" onClick={onClose}>完成</button>
    </div>
  );
}
window.RecordFlow = RecordFlow;
