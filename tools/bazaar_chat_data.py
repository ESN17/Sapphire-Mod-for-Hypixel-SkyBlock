"""Reviewed Bazaar notifications. Sources and coverage: docs/Bazaar聊天提示覆盖.md."""

MESSAGES = {
    '[Bazaar] Your Sell Offer for {value}x {name} was filled!': '[集市] 你的 {value}x {name} 卖单已全部成交!',
    '[Bazaar] Your Buy Order for {value}x {name} was filled!': '[集市] 你的 {value}x {name} 买单已全部成交!',
    '[Bazaar] Executing instant sell...': '[集市] 正在立即出售...',
    '[Bazaar] Claiming order...': '[集市] 正在领取订单...',
    '[Bazaar] Putting coins in escrow...': '[集市] 正在托管硬币...',
    '[Bazaar] Sold {value}x {name} for {market} coins!': '[集市] 已出售 {value}x {name},获得 {market} 硬币!',
    '[Bazaar] Order Flipped! {value}x {name} for {market} coins of total expected profit.': '[集市] 已转为卖单! {value}x {name},预计总利润 {market} 硬币.',
    '[Bazaar] Claimed {value}x {name} worth {market} coins bought for {marketBuy} each!': '[集市] 已领取 {value}x {name},总值 {market} 硬币,买入单价 {marketBuy}!',
    '[Bazaar] Claimed {market} coins from selling {value}x {name} at {marketSell} each!': '[集市] 已领取出售 {value}x {name} 获得的 {market} 硬币,卖出单价 {marketSell}!',
    '[Bazaar] Cancelled! Refunded {market} coins from cancelling Buy Order!': '[集市] 已取消买单! 已退回 {market} 硬币!',
    # Older capitalization is documented in SkyHanni's chat filter.
    '[Bazaar] Cancelled! Refunded {market} coins from cancelling buy order!': '[集市] 已取消买单! 已退回 {market} 硬币!',
    '[Bazaar] Cancelled! Refunded {value}x {name} from cancelling sell offer!': '[集市] 已取消卖单! 已退回 {value}x {name}!',
    'You have reached your maximum of {value} Bazaar orders!': '你已达到 {value} 个集市订单的上限!',
    'You reached the daily limit of coins you may create orders for on the Bazaar!': '你已达到今日集市下单金额上限!',
}

EXACT = {
    'Putting item in escrow...': '正在托管物品...',
    'Putting coins in escrow...': '正在托管硬币...',
    'Putting goods in escrow...': '正在托管物品...',
    'Submitting sell offer...': '正在提交卖单...',
    'Submitting buy order...': '正在提交买单...',
    'Executing instant sell...': '正在立即出售...',
    'Executing instant buy...': '正在立即购买...',
    'Cancelling order...': '正在取消订单...',
    'Claiming order...': '正在领取订单...',
    "It's currently claiming, chill!": '订单正在领取中,请稍候!',
    'Somehow, there was nothing to claim!': '没有可领取的内容!',
    'There was an error cancelling your order!': '取消订单时发生错误!',
    'Please try again later!': '请稍后重试!',
    "Couldn't match the quoted price!": '无法按报价成交!',
    'The sell order of this item was changed too much!': '该物品的卖单价格变化过大!',
    'Seems like a volatile market!': '市场价格波动较大!',
    'Underbidding too much!': '出价过低!',
    'Overbidding too much!': '出价过高!',
    'This server is too laggy to use the Bazaar/Auction House, sorry!': '当前服务器过于卡顿,暂时无法使用集市或拍卖行!',
}
for source, target in EXACT.items():
    MESSAGES.setdefault('[Bazaar] ' + source, '[集市] ' + target)

STYLES = {
    'Your': '你的', 'Sell Offer': '卖单', 'Buy Order': '买单',
    'was filled!': '已全部成交!', 'Sold': '已出售', 'Claimed': '已领取',
    'Order Flipped!': '已转为卖单!', 'worth': '总值',
    'bought for': '买入单价', 'at': '卖出单价',
}
