// ============ 我的 (settings) ============
// 修复：开关整行可点、role="switch"（原稿行是空按钮，只有 52px 小开关能点）；
//      平均策略可切换并真正作用于录入；记录天数来自数据；
//      无动作的行不再显示为可点击；导出提示明文风险（仓库 README 要求）
function SwitchRow({ ic, h, title, sub, on, onChange, dim }) {
  return (
    <button className={'row' + (dim ? ' dim' : '')} role="switch" disabled={dim} aria-checked={on} onClick={() => onChange(!on)}>
      <span className="ic" style={{ '--h': h }}><Icon n={ic} /></span>
      <span className="tx"><b>{title}</b>{sub && <small>{sub}</small>}</span>
      <span className={'tgl' + (on ? ' on' : '')} aria-hidden="true" />
    </button>
  );
}
function NavRow({ ic, h, title, sub, right, onClick }) {
  return (
    <button className="row" onClick={onClick}>
      <span className="ic" style={{ '--h': h }}><Icon n={ic} /></span>
      <span className="tx"><b>{title}</b>{sub && <small>{sub}</small>}</span>
      <span className="rt">{right}<Icon n="chev" /></span>
    </button>
  );
}

function MeScreen({ data, meds, reminders, openReminders, dark, setDark, big, setBig, buddyOn, setBuddyOn, strategy, setStrategy, toast }) {
  const [exp, setExp] = useState(false);
  const soon = (t) => () => toast(t + ' · 原型中未展开');
  return (
    <div className="screen screen-enter" data-screen-label="04 我的">
      <h1 className="h1">我的</h1>
      <div className="card profile" style={{ marginTop: 16, display: 'block' }}>
        <button style={{ display: 'flex', alignItems: 'center', gap: 14, width: '100%', textAlign: 'left' }} onClick={soon('编辑资料')} aria-label="编辑资料">
          <Buddy mood="happy" hue={32} size={0.56} />
          <div style={{ flex: 1 }}>
            <div className="nm">王阿姨</div>
            <div className="faint" style={{ fontSize: '.84rem', fontWeight: 500 }}>62 岁 · 女 · 已记录 {distinctDays(data)} 天</div>
          </div>
          <span className="icon-btn" aria-hidden="true"><Icon n="chev" /></span>
        </button>
        <div className="target">
          <div><span>目标高压</span><b className="num">&lt; {TARGET.s}</b></div>
          <div><span>目标低压</span><b className="num">&lt; {TARGET.d}</b></div>
        </div>
        <div style={{ marginTop: 14 }}>
          <div style={{ fontSize: '.8rem', fontWeight: 700, color: 'var(--ink2)', marginBottom: 6 }}>平均值怎么算</div>
          <div className="seg" role="radiogroup">
            <button role="radio" aria-checked={strategy === 'all'} className={strategy === 'all' ? 'on' : ''} onClick={() => setStrategy('all')}>全部组平均</button>
            <button role="radio" aria-checked={strategy === 'discardFirst'} className={strategy === 'discardFirst' ? 'on' : ''} onClick={() => setStrategy('discardFirst')}>不计第一组</button>
          </div>
          <div className="faint" style={{ fontSize: '.74rem', marginTop: 6, lineHeight: 1.5 }}>第一次读数常偏高，可弃用后取其余平均。只影响之后的新记录；高风险判断始终看全部读数。</div>
        </div>
      </div>

      <div className="sec-head"><h2 className="h2">提醒</h2></div>
      <div className="group">
        <NavRow ic="bell" h={32} title="提醒中心" sub={remindersSummary(reminders, meds)} right={<span style={{ fontSize: '.84rem' }}>设置</span>} onClick={openReminders} />
      </div>

      <div className="sec-head"><h2 className="h2">显示</h2></div>
      <div className="group">
        <SwitchRow ic="text" h={200} title="大字模式" sub="字号放大，按钮更好按" on={big} onChange={setBig} />
        <SwitchRow ic="moon" h={250} title="深色模式" on={dark} onChange={setDark} />
        <SwitchRow ic="heart" h={340} title="显示小压" sub="首页的小伙伴会跟着血压变表情" on={buddyOn} onChange={setBuddyOn} />
        <NavRow ic="widget" h={155} title="桌面小部件" sub="不打开 App 也能看最新血压" onClick={soon('桌面小部件')} />
      </div>

      <div className="sec-head"><h2 className="h2">数据</h2></div>
      <div className="group">
        <NavRow ic="export" h={155} title="导出为 Excel" sub="完整备份 · 格式 v4 · 明文" onClick={() => setExp(true)} />
        <NavRow ic="lock" h={280} title="加密备份 (.bpx)" sub="AES-256-GCM · 口令丢失无法恢复" onClick={soon('加密备份')} />
        <NavRow ic="folder" h={60} title="从备份导入" sub="先预览，按项勾选后再写入" onClick={soon('从备份导入')} />
        <NavRow ic="info" h={30} title="关于与更新说明" right={<span className="num" style={{ fontSize: '.84rem' }}>v2.0</span>} onClick={soon('关于')} />
      </div>

      <div className="privacy">
        <Icon n="shield" />
        <div><b>数据只在这台手机上。</b>不注册、不联网、不上传。卸载前请先导出备份，换机用备份恢复。</div>
      </div>

      {exp && <Confirm title="导出明文 Excel？" body="文件包含姓名、血压、用药等健康信息，任何拿到文件的人都能打开。请勿放入公共云盘；需要保密请选「加密备份」。" ok="继续导出"
        onOk={() => { setExp(false); toast(`已导出 血压记录_${toDateInput(TODAY)}.xlsx`); }} onCancel={() => setExp(false)} />}
    </div>
  );
}
Object.assign(window, { MeScreen, SwitchRow, NavRow });
