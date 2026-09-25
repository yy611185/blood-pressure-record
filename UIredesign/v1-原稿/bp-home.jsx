// ============ Home ============
function HomeScreen({ data, meds, setMeds, onRecord, go, buddyOn, toast }) {
  const latest = data[data.length - 1];
  const g = gradeOf(latest.s, latest.d);
  const now = TODAY;
  const todays = data.filter((r) => sameDay(r.t, now));
  const morning = todays.find((r) => r.t.getHours() < 12);
  const evening = todays.find((r) => r.t.getHours() >= 12);
  const [say, setSay] = useState(g.say);
  const [sayKey, setSayKey] = useState(0);
  const lines = [g.say, '戳我干嘛～ 测之前先静坐 5 分钟哦', '已经连续记录 12 天啦，继续保持！', '今晚 20:00 记得吃缬沙坦 💊'.replace(' 💊', '')];
  const poke = () => { setSay(lines[(lines.indexOf(say) + 1) % lines.length]); setSayKey((k) => k + 1); };

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

  const toggleMed = (id) => {
    setMeds((ms) => ms.map((m) => m.id === id ? { ...m, done: !m.done } : m));
    const m = meds.find((x) => x.id === id);
    if (m && !m.done) toast('已打卡 · ' + m.name);
  };

  return (
    <div className="screen screen-enter" data-screen-label="01 首页">
      <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between' }}>
        <div>
          <div className="eyebrow">9月25日 星期五</div>
          <h1 className="h1">{greet(now.getHours())}，王阿姨</h1>
        </div>
        <button className="icon-btn" onClick={() => go('me')} aria-label="提醒"><Icon n="bell" /></button>
      </div>

      {/* HERO */}
      <div className="hero g-soft" style={{ '--h': g.h }}>
        <div className="hero-deco" />
        {buddyOn && (
          <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start', position: 'relative', marginBottom: 6 }}>
            <Buddy mood={g.mood} hue={g.h} size={0.82} onClick={poke} />
            <div key={sayKey} className="bubble pop" style={{ marginTop: 8, flex: 1 }}>{say}</div>
          </div>
        )}
        <div style={{ position: 'relative' }}>
          <div className="eyebrow g-ink" style={{ '--h': g.h, fontWeight: 700, opacity: .8 }}>最近一次 · 今天 {hm(latest.t)} · {latest.scene}</div>
          <div className="reading num g-ink" style={{ '--h': g.h, marginTop: 6 }}>
            {latest.s}<span className="sl">/</span>{latest.d}<span className="unit">mmHg</span>
          </div>
          <div className="hero-meta">
            <GradeChip g={g} />
            <span className="g-ink" style={{ '--h': g.h, display: 'inline-flex', alignItems: 'center', gap: 4, fontWeight: 600 }}>
              <Icon n="heart" style={{ width: 16, height: 16 }} /> <span className="num">{latest.p}</span> 次/分
            </span>
            <span className="g-ink" style={{ '--h': g.h, opacity: .7, fontWeight: 500 }}>2 组平均</span>
          </div>
        </div>
      </div>

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
          <div className="pill" style={{ '--mh': m.mh, background: `oklch(0.94 0.04 ${m.mh})` }}><i /></div>
          <div>
            <div className="nm">{m.name}</div>
            <div className="ds"><span className="num">{m.time}</span> · {m.dose}</div>
          </div>
          <button className={'stamp' + (m.done ? ' done' : '')} onClick={() => toggleMed(m.id)}>
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
            <div className="big num">12 <span style={{ fontSize: '.85rem', fontFamily: 'Noto Sans SC', fontWeight: 700 }}>天</span></div>
            <div style={{ fontSize: '.76rem', fontWeight: 600, color: 'var(--accent-ink)', opacity: .8 }}>连续记录</div>
          </div>
          <div className="week-dots">{week.map((w, i) => <i key={i} className={w.s ? 'f' : ''}>{WD[w.d.getDay()]}</i>)}</div>
        </div>
        <div className="week">
          {week.map((w, i) => (
            <div key={i} className="wk-col">
              {w.s ? <span className="num" style={{ fontSize: '.7rem', fontWeight: 700, color: 'var(--ink2)' }}>{w.s}</span> : null}
              <div className="wk-bar" style={{ '--h': w.g ? w.g.h : 0, height: w.s ? Math.max(14, (w.s - 95) * 1.4) : 8, background: w.g ? `oklch(var(--gm-l) 0.12 ${w.g.h})` : 'var(--soft)' }} />
              <div className={'wk-lbl' + (i === 6 ? ' today' : '')}>{i === 6 ? '今' : WD[w.d.getDay()]}</div>
            </div>
          ))}
        </div>
      </div>
      <div className="faint" style={{ fontSize: '.74rem', textAlign: 'center', marginTop: 16 }}>分级仅供参考，不替代医疗诊断</div>
    </div>
  );
}
window.HomeScreen = HomeScreen;
