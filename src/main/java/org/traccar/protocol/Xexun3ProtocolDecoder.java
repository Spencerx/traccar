/*
 * Copyright 2026 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import org.traccar.BaseProtocolDecoder;
import org.traccar.NetworkMessage;
import org.traccar.Protocol;
import org.traccar.helper.Checksum;
import org.traccar.helper.UnitsConverter;
import org.traccar.model.Position;
import org.traccar.session.DeviceSession;

import java.net.SocketAddress;
import java.util.Date;

public class Xexun3ProtocolDecoder extends BaseProtocolDecoder {

    public Xexun3ProtocolDecoder(Protocol protocol) {
        super(protocol);
    }

    public static final int MSG_DATA = 0x20;
    public static final int MSG_COMMAND = 0x21;

    public static final int SUB_GPS = 0x64;

    private void sendResponse(Channel channel, int type, int index, ByteBuf imei) {
        if (channel != null) {
            ByteBuf response = Unpooled.buffer();
            response.writeByte(0xFC);
            response.writeShort(12); // length
            response.writeByte(0x03); // version
            response.writeByte(type);
            response.writeByte(index);
            response.writeBytes(imei, imei.readerIndex(), 8);
            response.writeByte(0); // result
            response.writeShort(Checksum.crc16(
                    Checksum.CRC16_CCITT_FALSE, response.nioBuffer(3, 12)));
            response.writeByte(0xCF);
            channel.writeAndFlush(new NetworkMessage(response, channel.remoteAddress()));
        }
    }

    @Override
    protected Object decode(
            Channel channel, SocketAddress remoteAddress, Object msg) throws Exception {

        ByteBuf buf = (ByteBuf) msg;

        buf.readUnsignedByte(); // header
        int length = buf.readUnsignedShort();
        buf.readUnsignedByte(); // version
        int type = buf.readUnsignedByte();
        int index = buf.readUnsignedByte();

        ByteBuf imei = buf.readSlice(8);
        DeviceSession deviceSession = getDeviceSession(
                channel, remoteAddress, ByteBufUtil.hexDump(imei).substring(1));
        if (deviceSession == null) {
            return null;
        }

        if (type != MSG_COMMAND) {
            sendResponse(channel, type, index, imei);
        }

        if (type != MSG_DATA) {
            return null;
        }

        Position position = new Position(getProtocolName());
        position.setDeviceId(deviceSession.getDeviceId());

        int bodyEnd = buf.readerIndex() + length - 11;
        while (buf.readerIndex() < bodyEnd) {
            int subType = buf.readUnsignedByte();
            int subLength = buf.readUnsignedByte();
            int subEnd = buf.readerIndex() + subLength;

            if (subType == SUB_GPS) {
                position.setTime(new Date(buf.readUnsignedInt() * 1000));
                position.setValid(true);
                position.setLatitude(buf.readDouble());
                position.setLongitude(buf.readDouble());
                position.setAltitude(buf.readFloat());
                buf.readUnsignedByte(); // ephemeris
                position.set(Position.KEY_SATELLITES, buf.readUnsignedByte());
                buf.readUnsignedByte(); // signal
                position.setSpeed(UnitsConverter.knotsFromKph(buf.readUnsignedShort()));
            }

            buf.readerIndex(subEnd);
        }

        return position.getDeviceTime() != null ? position : null;
    }

}
