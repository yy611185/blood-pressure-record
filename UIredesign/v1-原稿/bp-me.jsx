// ============ 我的 (settings) ============
function MeScreen({ dark, setDark, big, setBig, buddyOn, setBuddyOn, toast }) {
  const [am, setAm] = useState(true);
  const [pm, setPm] = useState(true);
  const Row = ({ ic, h, title, sub, right, onClick }) => (
    <button className="row" onClick={onClick}>
      <span className="ic" style={{ '--h': h }}><Icon n={ic} /></span>
      <span className="tx"><b>{title}</b>{sub && <small>{sub}</small>}</span>
      <span className="rt">{right !== undefined ? right : <Icon n="chev" />}</span>
    </button>
  );
  return (
    <div className="screen screen-enter" data-screen-label="04 我的">
      <h1 className="h1">我的</h1>
      <div className="card profile" style={{ marginTop: 16, display: 'block' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
          <Buddy mood="happy" hue={32} size={0.56} />
          <div style={{ flex: 1 }}>
            <div className="nm">王阿姨</div>
            <div className="faint" style={{ fontSize: '.84rem', fontWeight: 500 }}>62 岁 · 女 · 已记录 59 天</div>
          </div>
          <button className="icon-btn" aria-label="编辑资料"><Icon n="chev" /></button>
        </div>
        <div className="target">
          <div><span>目标高压</span><b className="num">&lt; 135</b></div>
          <div><span>目标低压</span><b className="num">&lt; 85</b></div>
          <div><span>平均策略</span><b style={{ fontSize: '.95rem' }}>全部组</b></div>
        </div>
      </div>

      <div className="sec-head"><h2 className="h2">提醒</h2></div>
      <div className="group">
        <Row ic="sun" h={75} title="早上测量" sub={<span className="num">07:30</span>} right={<span className={'tgl' + (am ? ' on' : '')} onClick={(e) => { e.stopPropagation(); setAm(!am); }} />} />
        <Row ic="moon" h={270} title="晚上测量" sub={<span className="num">21:00</span>} right={<span className={'tgl' + (pm ? ' on' : '')} onClick={(e) => { e.stopPropagation(); setPm(!pm); }} />} />
        <Row ic="pill" h={20} title="药品与服药时间" sub="2 种药 · 同步到系统日历" right={<><span style={{ fontSize: '.84rem' }}>管理</span><Icon n="chev" /></>} />
      </div>

      <div className="sec-head"><h2 className="h2">显示</h2></div>
      <div className="group">
        <Row ic="text" h={200} title="大字模式" sub="字号放大，按钮更好按" right={<span className={'tgl' + (big ? ' on' : '')} onClick={(e) => { e.stopPropagation(); setBig(!big); }} />} />
        <Row ic="moon" h={250} title="深色模式" right={<span className={'tgl' + (dark ? ' on' : '')} onClick={(e) => { e.stopPropagation(); setDark(!dark); }} />} />
        <Row ic="heart" h={340} title="显示小压" sub="首页的小伙伴会跟着血压变表情" right={<span className={'tgl' + (buddyOn ? ' on' : '')} onClick={(e) => { e.stopPropagation(); setBuddyOn(!buddyOn); }} />} />
        <Row ic="widget" h={155} title="桌面小部件" sub="不打开 App 也能看最新血压" />
      </div>

      <div className="sec-head"><h2 className="h2">数据</h2></div>
      <div className="group">
        <Row ic="export" h={155} title="导出为 Excel" sub="完整备份 · 格式 v4" onClick={() => toast('已导出 血压记录_2026-09-25.xlsx')} />
        <Row ic="lock" h={280} title="加密备份 (.bpx)" sub="AES-256 · 需要口令才能恢复" />
        <Row ic="folder" h={60} title="从备份导入" sub="先预览，确认后再写入" />
        <Row ic="info" h={30} title="关于与更新说明" right={<span className="num" style={{ fontSize: '.84rem' }}>v2.0</span>} />
      </div>

      <div className="privacy">
        <Icon n="shield" />
        <div><b>数据只在这台手机上。</b>不注册、不联网、不上传。换机请用导出备份。</div>
      </div>
    </div>
  );
}
window.MeScreen = MeScreen;
