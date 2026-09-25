// ============ Record flow: 静坐 → 录入 → 情况 → 完成 ============
// 修复要点（对照仓库）：
//  · 至少 2 组才能继续（MIN_READING_COUNT=2），最多 10 组（UI_MAX_READING_COUNT）
//  · 逐组校验 40–300 / 20–200 / 20–250 / 低压<高压，并显示具体错误
//  · 脉搏选填，不再伪造 72；平均策略（全部 / 弃第一组）真正生效
//  · 高风险（任一组或平均 ≥180/120）独立提醒
//  · 测量时间可改（补录），拒绝晚于当前 2 分钟以上；场景按时间自动预选
//  · 症状被保存；「偏离常见范围」保存前二次确认；有输入时关闭需确认
const FIELDS = [{ k: 's', name: '收缩压', sub: '高压' }, { k: 'd', name: '舒张压', sub: '低压' }, { k: 'p', name: '脉搏', sub: '选填' }];
// 2 位数时无法再加第 3 位（超出范围上限）→ 自动跳下一项
const AUTO2 = { s: 30, d: 20, p: 25 };
const REST_SEC = 300;
const emptyG = () => ({ s: '', d: '', p: '' });

function RecordFlow({ onClose, onSave, buddyOn, skipRest, data, strategy, initial }) {
  const editing = !!initial;
  const [step, setStep] = useState(skipRest || editing ? 1 : 0);
  const firstStep = skipRest || editing ? 1 : 0;
  const [out, setOut] = useState(false);
  const [groups, setGroups] = useState(() => initial ? initial.groups.map((g) => ({ s: String(g.s), d: String(g.d), p: g.p == null ? '' : String(g.p) })) : [emptyG(), emptyG()]);
  const [gi, setGi] = useState(0);
  const [fi, setFi] = useState(0);
  const [when, setWhen] = useState(() => new Date(initial ? initial.t : TODAY));
  const [scene, setScene] = useState(initial ? initial.scene : defaultSceneFor(TODAY.getHours()));
  const [sceneTouched, setSceneTouched] = useState(editing);
  const [sym, setSym] = useState(initial ? (initial.symptoms && initial.symptoms.length ? initial.symptoms : ['无症状']) : ['无症状']);
  const [fac, setFac] = useState(initial ? initial.tags : []);
  const [note, setNote] = useState(initial ? initial.note : '');
  const [sec, setSec] = useState(REST_SEC);
  const [dlg, setDlg] = useState(null);
  const [saved, setSaved] = useState(null);

  useEffect(() => {
    if (step !== 0) return;
    const t = setInterval(() => setSec((s) => (s <= 1 ? (clearInterval(t), 0) : s - 1)), 1000);
    return () => clearInterval(t);
  }, [step]);

  // 上一次记录（编辑时排除自身）
  const prev = useMemo(() => {
    const selfId = initial ? initial.id : saved ? saved.id : null;
    const list = data.filter((r) => r.id !== selfId && r.t <= when).sort((a, b) => a.t - b.t);
    return list[list.length - 1] || null;
  }, [data, when, saved]);

  const errs = groups.map(groupError);
  const validGroups = groups.filter((g, i) => errs[i] === null);
  const blockingErr = errs.map((e, i) => (e && e !== 'empty' ? { i, e } : null)).filter(Boolean)[0];
  const enoughGroups = validGroups.length >= RULES.minGroups;
  const canNext = enoughGroups && !blockingErr;
  const avg = validGroups.length ? averageOf(validGroups, strategy) : null;
  const liveG = avg ? gradeOf(avg.s, avg.d) : null;
  const risk = containsHighRisk(validGroups, avg);
  const hasInput = groups.some((g) => g.s || g.d || g.p) || note || fac.length;

  const future = when.getTime() > TODAY.getTime() + 2 * 60 * 1000;

  const tryClose = () => {
    if (step !== 3 && hasInput && !editing) {
      setDlg({ title: '放弃这次记录？', body: '已填写的读数不会保存。', ok: '放弃', danger: true, onOk: () => { setDlg(null); doClose(); } });
    } else if (step !== 3 && editing) {
      setDlg({ title: '放弃修改？', body: '这条记录会保持修改前的样子。', ok: '放弃修改', danger: true, onOk: () => { setDlg(null); doClose(); } });
    } else doClose();
  };
  const doClose = () => { setOut(true); setTimeout(() => onClose(saved), 280); };

  const advance = (fromG, fromF) => {
    if (fromF < 2) { setFi(fromF + 1); return; }
    if (fromG < groups.length - 1) { setGi(fromG + 1); setFi(0); }
  };
  const press = (k) => {
    const f = FIELDS[fi].k; let v = groups[gi][f];
    if (k === 'del') v = v.slice(0, -1);
    else if (v.length >= 3 || (v === '' && k === '0')) return; // 不允许前导 0
    else v = v + k;
    setGroups((gs) => gs.map((g, i) => (i === gi ? { ...g, [f]: v } : g)));
    const auto = k !== 'del' && (v.length === 3 || (v.length === 2 && +v > AUTO2[f]));
    if (auto) { const g0 = gi, f0 = fi; setTimeout(() => advance(g0, f0), 120); }
  };
  const addGroup = () => { if (groups.length >= RULES.uiMaxGroups) return; setGroups((gs) => [...gs, emptyG()]); setGi(groups.length); setFi(0); };
  const removeGroup = (i) => { setGroups((gs) => gs.filter((_, j) => j !== i)); setGi(Math.max(0, Math.min(gi, groups.length - 2))); setFi(0); };
  const isLastField = fi === 2 && gi === groups.length - 1;

  const setDate = (v) => { if (!v) return; const [y, m, d] = v.split('-').map(Number); const n = new Date(when); n.setFullYear(y, m - 1, d); setWhen(n); };
  const setTime = (v) => {
    if (!v) return; const [h, mi] = v.split(':').map(Number); const n = new Date(when); n.setHours(h, mi); setWhen(n);
    if (!sceneTouched) setScene(defaultSceneFor(h));
  };
  const toggleSym = (s) => {
    if (s === '无症状') return setSym(['无症状']);
    const base = sym.filter((x) => x !== '无症状');
    const n = base.includes(s) ? base.filter((x) => x !== s) : [...base, s];
    setSym(n.length ? n : ['无症状']);
  };
  const toggleFac = (s) => setFac(fac.includes(s) ? fac.filter((x) => x !== s) : [...fac, s]);

  const commit = () => {
    const rec = {
      id: initial ? initial.id : 'n' + Date.now(), t: new Date(when), s: avg.s, d: avg.d, p: avg.p,
      groups: validGroups.map((g) => ({ s: +g.s, d: +g.d, p: g.p ? +g.p : null })), strategy,
      scene, symptoms: sym.filter((x) => x !== '无症状').length ? sym : ['无症状'], tags: fac, note: note.trim(),
    };
    onSave(rec, editing);
    setSaved(rec); setStep(3);
  };
  const save = () => {
    if (future) return;
    const ab = abnormalMessage(validGroups);
    if (ab) setDlg({ title: '读数有点不寻常', body: `${ab} 偏离常见范围。请确认没有输错，是否继续保存？`, ok: '确认保存', onOk: () => { setDlg(null); commit(); } });
    else commit();
  };

  const dangerSym = sym.some((s) => DANGER_SYMPTOMS.includes(s));

  return (
    <div className={'flow' + (out ? ' out' : '')} data-screen-label={editing ? '06 编辑记录' : '05 记一次血压'}>
      <div className="flow-head">
        {step > firstStep && step < 3
          ? <button className="icon-btn" onClick={() => setStep(step - 1)} aria-label="上一步"><Icon n="back" /></button>
          : <button className="icon-btn" onClick={tryClose} aria-label="关闭"><Icon n="close" /></button>}
        <div className="ttl">{[`先静坐一会儿`, editing ? '修改读数' : '记一次血压', '测的时候怎么样？', editing ? '已更新' : '记好啦'][step]}</div>
        <div className="steps" aria-label={`第 ${step + 1} 步，共 4 步`}>{[0, 1, 2, 3].map((i) => <i key={i} className={i === step ? 'on' : i < step ? 'past' : ''} />)}</div>
      </div>

      {step === 0 && (
        <div className="flow-body">
          <div className="rest">
            <div className="breath"><div className="ring" /><div className="ring r2" /><div className="ring r3" /><div className="lbl">跟着呼吸</div></div>
            <div className="timer num" aria-live="off">{Math.floor(sec / 60)}:{pad2(sec % 60)}</div>
            <div className="muted" style={{ fontSize: '.95rem', lineHeight: 1.7, maxWidth: 290 }}>测量前静坐 5 分钟，坐直、双脚平放、<br />手臂与心脏同高。圈变大吸气，变小呼气。</div>
          </div>
          <button className="btn btn-primary btn-block" onClick={() => setStep(1)}>{sec === 0 ? '准备好了，开始记录' : '我已经静坐过了，直接记录'}</button>
        </div>
      )}

      {step === 1 && (
        <div className="flow-body" style={{ paddingBottom: 24 }}>
          <div className="pad-top">
            <div className="gtabs" role="tablist">
              {groups.map((g, i) => (
                <button key={i} role="tab" aria-selected={i === gi} className={'gtab' + (i === gi ? ' on' : '') + (errs[i] === null ? ' ok' : errs[i] !== 'empty' ? ' bad' : '')} onClick={() => { setGi(i); setFi(0); }}>
                  第{i + 1}组
                  {i >= RULES.minGroups && i === gi && <span className="gx" role="button" aria-label={`删除第${i + 1}组`} onClick={(e) => { e.stopPropagation(); removeGroup(i); }}><Icon n="close" sw={2.6} /></span>}
                </button>
              ))}
              {groups.length < RULES.uiMaxGroups && <button className="gtab add" onClick={addGroup}><Icon n="plus" style={{ width: 14, height: 14 }} />加一组</button>}
            </div>
          </div>
          <div className="cells">
            {FIELDS.map((f, i) => {
              const v = groups[gi][f.k];
              return (
                <button key={f.k} className={'cell' + (i === 0 ? ' big' : '') + (i === fi ? ' on' : '')} onClick={() => setFi(i)} aria-label={`${f.name}${v ? ' ' + v : ' 未填写'}`}>
                  <div>
                    <div className="k">{f.name}<small>{f.sub}</small></div>
                    <div className="val num">
                      {v ? v : <span className="ph">—</span>}
                      {i === fi && <span className="caret" />}
                    </div>
                  </div>
                  {i === 0 && prev && <div className="prev-hint">上次 <b className="num">{prev.s}/{prev.d}</b></div>}
                </button>
              );
            })}
          </div>
          {blockingErr ? (
            <div className="avg-strip err" role="alert"><Icon n="alert" style={{ width: 18, height: 18, flex: 'none' }} /><span><b>第{blockingErr.i + 1}组：</b>{blockingErr.e}</span></div>
          ) : risk ? (
            <div className="avg-strip risk" role="alert"><Icon n="alert" style={{ width: 18, height: 18, flex: 'none' }} /><span>有读数 ≥180/120，属于<b>高风险</b>。请休息后复测，不适请及时就医。</span></div>
          ) : (
            <div className="avg-strip">
              {avg && enoughGroups ? <>
                <span className="muted" style={{ fontWeight: 600 }}>{strategy === 'discardFirst' ? `弃第1组 · ${avg.n} 组平均` : `${avg.n} 组平均`}</span>
                <b className="num" style={{ fontSize: '1.1rem' }}>{avg.s}/{avg.d}</b>
                {avg.p && <span className="faint num">♥ {avg.p}</span>}
                <span style={{ marginLeft: 'auto' }}><GradeChip g={liveG} /></span>
              </> : <span className="faint">{validGroups.length === 1 ? '再测一组就可以啦 · 间隔 1–2 分钟' : '至少记 2 组，间隔 1–2 分钟，取平均更准'}</span>}
            </div>
          )}
          <div className="keypad">
            {['1', '2', '3', '4', '5', '6', '7', '8', '9'].map((k) => <button key={k} className="key num" onClick={() => press(k)}>{k}</button>)}
            <button className="key fn" onClick={() => press('del')} aria-label="删除"><Icon n="del" /></button>
            <button className="key num" onClick={() => press('0')}>0</button>
            {canNext && (isLastField || validGroups.length === groups.length)
              ? <button className="key fn go" onClick={() => setStep(2)}>下一步</button>
              : <button className="key fn" onClick={() => advance(gi, fi)} disabled={isLastField}>下一项</button>}
          </div>
        </div>
      )}

      {step === 2 && (
        <div className="flow-body">
          <div className="lbl-q">什么时候测的？<small>可补录</small></div>
          <div className="when">
            <label><Icon n="cal" /><input type="date" className="num" value={toDateInput(when)} max={toDateInput(TODAY)} onChange={(e) => setDate(e.target.value)} aria-label="测量日期" /></label>
            <label style={{ flex: '0 0 124px' }}><Icon n="clock" /><input type="time" className="num" value={hm(when)} onChange={(e) => setTime(e.target.value)} aria-label="测量时间" /></label>
          </div>
          {future && <div className="field-err" role="alert">测量时间不能晚于当前时间 2 分钟以上</div>}
          <div className="chips" style={{ marginTop: 10 }}>{SCENES.map((s) => <button key={s} className={'chip' + (scene === s ? ' on' : '')} aria-pressed={scene === s} onClick={() => { setScene(s); setSceneTouched(true); }}>{s}</button>)}</div>
          <div className="lbl-q">有没有不舒服？</div>
          <div className="chips">{SYMPTOMS.map((s) => <button key={s} className={'chip' + (sym.includes(s) ? ' on' : '')} aria-pressed={sym.includes(s)} onClick={() => toggleSym(s)}>{s}</button>)}</div>
          {dangerSym && (risk || (liveG && (liveG.key === 'g2' || liveG.key === 'g3'))) && (
            <div className="avg-strip risk" style={{ marginTop: 10 }} role="alert"><Icon n="alert" style={{ width: 18, height: 18, flex: 'none' }} /><span>血压偏高且伴有{sym.filter((s) => DANGER_SYMPTOMS.includes(s)).join('、')}，<b>请尽快就医</b>或拨打 120。</span></div>
          )}
          <div className="lbl-q">可能影响血压的情况<small>可多选</small></div>
          <div className="chips">{FACTORS.map((s) => <button key={s} className={'chip' + (fac.includes(s) ? ' on' : '')} aria-pressed={fac.includes(s)} onClick={() => toggleFac(s)}>{s}</button>)}</div>
          <div className="lbl-q">备注<small>选填</small></div>
          <textarea className="note" placeholder="比如「早饭前测的」" value={note} maxLength={200} onChange={(e) => setNote(e.target.value)} />
          <div style={{ height: 16, flex: 'none' }} />
          <button className="btn btn-primary btn-block" style={{ flex: 'none' }} disabled={future} onClick={save}>{editing ? '保存修改' : '保存这次记录'} · <span className="num">{avg.s}/{avg.d}</span></button>
        </div>
      )}

      {step === 3 && saved && <DoneStep rec={saved} g={gradeOf(saved.s, saved.d)} risk={containsHighRisk(saved.groups, saved)} prev={prev} data={data} onClose={doClose} buddyOn={buddyOn} editing={editing} />}

      {dlg && <Confirm {...dlg} onCancel={() => setDlg(null)} />}
    </div>
  );
}

function DoneStep({ rec, g, risk, prev, data, onClose, buddyOn, editing }) {
  const good = !risk && (g.key === 'ok' || g.key === 'hn');
  const pieces = useMemo(() => Array.from({ length: 36 }, (_, i) => ({
    left: Math.random() * 100, delay: Math.random() * .6, h: [32, 155, 250, 90, 340][i % 5], rot: Math.random() * 360, w: 6 + Math.random() * 8,
  })), []);
  const ds = prev ? rec.s - prev.s : 0;
  const streak = streakOf(data);
  return (
    <div className="flow-body">
      {good && !editing && <div className="confetti">{pieces.map((p, i) => <i key={i} style={{ left: p.left + '%', animationDelay: p.delay + 's', background: `oklch(0.78 0.14 ${p.h})`, width: p.w, transform: `rotate(${p.rot}deg)` }} />)}</div>}
      <div className="done-step">
        {buddyOn ? <Buddy mood={risk ? 'worried' : g.mood} hue={g.h} size={1.2} /> : <div className="g-mid" style={{ '--h': g.h, width: 96, height: 96, borderRadius: 32, display: 'grid', placeItems: 'center', color: '#fff' }}><Icon n={risk ? 'alert' : 'check'} sw={3} style={{ width: 44, height: 44 }} /></div>}
        <div className="reading num g-ink" style={{ '--h': g.h }}>{rec.s}<span style={{ opacity: .3, fontWeight: 300 }}>/</span>{rec.d}</div>
        <div style={{ marginTop: 10, display: 'flex', gap: 6, justifyContent: 'center' }}><GradeChip g={g} />{risk && <RiskChip />}</div>
        <div className={'bubble' + (risk ? ' risk' : '')} style={{ marginTop: 18, borderRadius: 18, maxWidth: 320 }}>{risk ? HIGH_RISK_SAY : g.say}</div>
        <div className="compare">
          <div><span>和上次比 · 高压</span><b className="num" style={{ color: !prev ? 'var(--ink3)' : ds > 0 ? 'oklch(0.6 0.15 35)' : 'oklch(0.55 0.12 155)' }}>{!prev ? '—' : `${ds > 0 ? '↑' : ds < 0 ? '↓' : '='} ${Math.abs(ds)}`}</b></div>
          <div><span>脉搏</span><b className="num">{rec.p || '—'}</b></div>
          <div><span>连续记录</span><b className="num">{streak} 天</b></div>
        </div>
      </div>
      <button className="btn btn-primary btn-block" onClick={onClose}>完成</button>
    </div>
  );
}
window.RecordFlow = RecordFlow;
