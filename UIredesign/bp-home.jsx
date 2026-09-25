// ============ Home ============
// 修复：最近一次的日期/组数/连续天数全部来自数据；无记录时有空状态；
//      高风险独立提示；周柱高度钳制；取消打卡有反馈并可撤销
function HomeScreen({ data, meds, setMeds, onRecord, go, buddyOn, toast }) {
  const latest = data[data.length - 1] || null;
  const g = latest ? gradeOf(latest.s, latest.d) : null;
  const risk = latest ? containsHighRisk(latest.groups, latest) : false;
  const now = TODAY;
  const todays = data.filter((r) => sameDay(r.t, now));
  const morning = todays.filter((r) => r.t.getHours() < 12).slice(-1)[0];
  const evening = todays.filter((r) => r.t.getHours() >= 12).slice(-1)[0];
  const streak = streakOf(data);
  const nextMed = meds.find((m) => !m.done);

  const baseSay = !latest ? '你好呀，我是小压！测完血压记下来，我会陪着你～' : risk ? '这次读数很高，先休息一下，再复测一次好吗？' : g.say;
  const lines = useMemo(() => [
    baseSay, '戳我干嘛～ 测之前先静坐 5 分钟哦',
    streak > 1 ? `已经连续记录 ${streak} 天啦，继续保持！` : '每天早晚各测一次，我会帮你记着～',
    nextMed ? `今天 ${nextMed.time} 记得吃${nextMed.name}` : '今天的药都吃过啦，真棒！',
  ], [baseSay, streak, nextMed && nextMed.id]);
  const [li, setLi] = useState(0);
  useEffect(() => setLi(0), [baseSay]);
  const poke = () => setLi((i) => (i + 1) % lines.length);

  // last 7 days averages
  const week = useMemo(() => {
    const out = [];
    for (let i = 6; i >= 0; i--) {
      const d = new Date(now); d.setDate(d.getDate() - i);
      const rs = data.filter((r) => sameDay(r.t, d));
      const s = rs.length ? Math.round(rs.reduce((a, r) => a + r.s, 0) / rs.length) : null;
      const dd = rs.length ? Math.round(rs.reduce((a, r) => a + r.d, 0) / rs.length) : null;
      out.push({ d, s, dd, g: gradeOf(s, dd) });
    }
    return out;
  }, [data]);
  const barH = (s) => Math.max(14, Math.min(84, (s - 95) * 1.4));

  const toggleMed = (id) => {
    const m = meds.find((x) => x.id === id);
    setMeds((ms) => ms.map((x) => x.id === id ? { ...x, done: !x.done } : x));
    toast(m.done ? '已取消打卡 · ' + m.name : '已打卡 · ' + m.name);
  };

  return (
    <div className="screen screen-enter" data-screen-label="01 首页">
      <div>
        <div className="eyebrow">{mdw(now)}</div>
        <h1 className="h1">{greet(now.getHours())}，王阿姨</h1>
      </div>

      {/* HERO */}
      {latest ? (
        <div className={'hero g-soft' + (risk ? ' is-risk' : '')} style={{ '--h': g.h }}>
          <div className="hero-deco" />
          {buddyOn && (
            <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start', position: 'relative', marginBottom: 6 }}>
              <Buddy mood={risk ? 'worried' : g.mood} hue={g.h} size={0.82} onClick={poke} label="小压，点一下换一句话" />
              <div key={li + baseSay} className="bubble pop" style={{ marginTop: 8, flex: 1 }} aria-live="polite">{lines[li]}</div>
            </div>
          )}
          <div style={{ position: 'relative' }}>
            <div className="eyebrow g-ink" style={{ '--h': g.h, fontWeight: 700, opacity: .8 }}>最近一次 · {relDay(latest.t)} {hm(latest.t)} · {latest.scene}</div>
            <div className="reading num g-ink" style={{ '--h': g.h, marginTop: 6 }}>
              {latest.s}<span className="sl">/</span>{latest.d}<span className="unit">mmHg</span>
            </div>
            <div className="hero-meta">
              <GradeChip g={g} />
              {risk && <RiskChip />}
              {latest.p != null && <span className="g-ink" style={{ '--h': g.h, display: 'inline-flex', alignItems: 'center', gap: 4, fontWeight: 600 }}>
                <Icon n="heart" style={{ width: 16, height: 16 }} /> <span className="num">{latest.p}</span> 次/分
              </span>}
              <span className="g-ink" style={{ '--h': g.h, opacity: .7, fontWeight: 500 }}>{latest.strategy === 'discardFirst' && latest.groups.length >= 2 ? `弃第1组 · ${latest.groups.length - 1} 组平均` : `${latest.groups.length} 组平均`}</span>
            </div>
            {risk && <div className="risk-note">有读数达到 180/120 以上。请静坐休息后复测；伴胸痛、剧烈头痛等请立即就医。</div>}
          </div>
        </div>
      ) : (
        <div className="hero g-soft" style={{ '--h': 32 }}>
          <div className="hero-deco" />
          <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start', position: 'relative' }}>
            {buddyOn && <Buddy mood="idle" hue={32} size={0.82} />}
            <div className="bubble" style={{ marginTop: 8, flex: 1 }}>{baseSay}</div>
          </div>
          <button className="btn btn-primary btn-block" style={{ marginTop: 14, position: 'relative' }} onClick={onRecord}><Icon n="plus" sw={3} style={{ width: 20, height: 20 }} />记第一次血压</button>
        </div>
      )}

      {/* Today slots */}
      <div className="sec-head"><h2 className="h2">今天的测量</h2><span className="more">早晚各一次最好</span></div>
      <div className="slots">
        {[{ k: '早上', ic: 'sun', r: morning }, { k: '晚上', ic: 'moon', r: evening }].map((x) => x.r ? (
          <div key={x.k} className="slot">
            <div className="lbl"><Icon n={x.ic} style={{ width: 16, height: 16 }} />{x.k} · {hm(x.r.t)}</div>
            <div>
              <div className="v num">{x.r.s}/{x.r.d}</div>
              <div style={{ marginTop: 4 }}><GradeChip g={gradeOf(x.r.s, x.r.d)} /></div>
            </div>
          </div>
        ) : (
          <button key={x.k} className="slot pending" onClick={onRecord}>
            <div className="lbl"><Icon n={x.ic} style={{ width: 16, height: 16 }} />{x.k} · 待测</div>
            <div className="go">去测一次 <Icon n="chev" style={{ width: 16, height: 16 }} /></div>
          </button>
        ))}
      </div>

      {/* Meds */}
      <div className="sec-head"><h2 className="h2">今日服药</h2><span className="more">{meds.filter((m) => m.done).length}/{meds.length} 已打卡</span></div>
      {meds.map((m) => (
        <div key={m.id} className="med">
          <div className="pill" style={{ '--mh': m.mh, background: `oklch(var(--gs-l) 0.04 ${m.mh})` }}><i /></div>
          <div style={{ minWidth: 0 }}>
            <div className="nm">{m.name}</div>
            <div className="ds"><span className="num">{m.time}</span> · {m.dose}</div>
          </div>
          <button className={'stamp' + (m.done ? ' done' : '')} aria-pressed={m.done} aria-label={m.done ? `${m.name} 已吃，点击取消` : `${m.name} 打卡`} onClick={() => toggleMed(m.id)}>
            {m.done ? <span style={{ display: 'inline-flex', alignItems: 'center', gap: 2 }}><Icon n="check" sw={3} style={{ width: 16, height: 16 }} />吃了</span> : '打卡'}
          </button>
        </div>
      ))}

      {/* Week */}
      <div className="sec-head"><h2 className="h2">这一周</h2><button className="more" onClick={() => go('trend')}>看趋势 <Icon n="chev" style={{ width: 14, height: 14 }} /></button></div>
      <div className="card" style={{ padding: 0 }}>
        <div className="streak" style={{ margin: 8, marginBottom: 0 }}>
          <Icon n="flame" style={{ width: 28, height: 28, color: 'var(--accent)' }} />
          <div>
            <div className="big num">{streak} <span style={{ fontSize: '.85rem', fontFamily: 'Noto Sans SC', fontWeight: 700 }}>天</span></div>
            <div style={{ fontSize: '.76rem', fontWeight: 600, color: 'var(--accent-ink)', opacity: .8 }}>连续记录</div>
          </div>
          <div className="week-dots" aria-label={`近 7 天有 ${week.filter((w) => w.s).length} 天测量`}>{week.map((w, i) => <i key={i} className={w.s ? 'f' : ''}>{i === 6 ? '今' : WD[w.d.getDay()]}</i>)}</div>
        </div>
        <div className="week">
          {week.map((w, i) => (
            <div key={i} className="wk-col" aria-label={w.s ? `周${WD[w.d.getDay()]} 平均 ${w.s}/${w.dd} ${w.g.name}` : `周${WD[w.d.getDay()]} 未测量`}>
              {w.s ? <span className="num" style={{ fontSize: '.7rem', fontWeight: 700, color: 'var(--ink2)' }}>{w.s}</span> : null}
              <div className="wk-bar" style={{ height: w.s ? barH(w.s) : 8, background: w.g ? `oklch(var(--gm-l) 0.12 ${w.g.h})` : 'var(--soft)' }} />
              <div className={'wk-lbl' + (i === 6 ? ' today' : '')}>{i === 6 ? '今' : WD[w.d.getDay()]}</div>
            </div>
          ))}
        </div>
        <div className="faint" style={{ fontSize: '.72rem', padding: '0 16px 12px', marginTop: -4 }}>柱子数字为当天收缩压平均值</div>
      </div>
      <div className="faint" style={{ fontSize: '.74rem', textAlign: 'center', marginTop: 16 }}>分级仅供参考，不替代医疗诊断</div>
    </div>
  );
}
window.HomeScreen = HomeScreen;
