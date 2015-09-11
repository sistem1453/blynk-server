package cc.blynk.server.handlers.hardware.logic;

import cc.blynk.common.model.messages.Message;
import cc.blynk.common.model.messages.protocol.HardwareMessage;
import cc.blynk.common.utils.StringUtils;
import cc.blynk.server.dao.SessionsHolder;
import cc.blynk.server.dao.graph.GraphKey;
import cc.blynk.server.exceptions.IllegalCommandException;
import cc.blynk.server.exceptions.NoActiveDashboardException;
import cc.blynk.server.model.auth.ChannelState;
import cc.blynk.server.model.auth.Session;
import cc.blynk.server.model.auth.User;
import cc.blynk.server.storage.StorageDao;
import io.netty.channel.ChannelHandlerContext;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/1/2015.
 *
 */
public class HardwareLogic {

    private final StorageDao storageDao;
    private final SessionsHolder sessionsHolder;

    public HardwareLogic(SessionsHolder sessionsHolder, StorageDao storageDao) {
        this.sessionsHolder = sessionsHolder;
        this.storageDao = storageDao;
    }

    private static String attachTS(String body, long ts) {
        return body + StringUtils.BODY_SEPARATOR_STRING + ts;
    }

    public void messageReceived(ChannelHandlerContext ctx, User user, Message message) {
        Session session = sessionsHolder.userSession.get(user);

        //if message from hardware, check if it belongs to graph. so we need save it in that case
        if (message.body.length() < 4) {
            throw new IllegalCommandException("HardwareLogic command body too short.", message.id);
        }

        String body = message.body;
        long ts = System.currentTimeMillis();

        if (body.charAt(1) == 'w') {
            Integer dashId = ctx.channel().attr(ChannelState.DASH_ID).get();

            GraphKey key = new GraphKey(dashId, body, ts);

            //storing to DB and aggregating
            storageDao.process(key);

            //in case message is for graph - attaching ts.
            if (user.getProfile().hasGraphPin(key)) {
                body = attachTS(body, ts);
            }
        }

        if (user.getProfile().activeDashId == null || !user.getProfile().activeDashId.equals(ctx.channel().attr(ChannelState.DASH_ID).get())) {
            throw new NoActiveDashboardException(message.id);
        }

        if (session.appChannels.size() > 0) {
            session.sendMessageToApp(((HardwareMessage) message).updateMessageBody(body));
        }
    }

}
