/*
 * The MIT License
 *
 * Copyright (c) 2017, aoju.org All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.aoju.bus.socket.netty;

/**
 * @author Kimi Liu
 * @version 3.1.8
 * @since JDK 1.8
 */
public class CancelCommand implements Runnable {

    private SocketRequest request;

    public CancelCommand(SocketRequest request) {
        this.request = request;
    }

    @Override
    public void run() {
        for (String topic : request.getTopic()) {
            if (NettyConsts.TOPIC_ALL.equalsIgnoreCase(topic)) {
                cancel(request.getData());
                break;
            } else {
                cancel(topic, request.getData());
            }
        }
    }

    private void cancel(String data) {
        ClientGroup group = ClientService.getClientGroup();
        for (ClientMap map : group.values()) {
            if (map.containsKey(request.getContext().channel().id())) {
                SocketClient client = map.get(request.getContext().channel().id());
                client.cancel(data);
            }
        }
    }

    private void cancel(String topic, String data) {
        ClientGroup group = ClientService.getClientGroup();
        if (group.containsKey(topic)) {
            ClientMap map = group.get(topic);
            if (map.containsKey(request.getContext().channel().id())) {
                SocketClient client = map.get(request.getContext().channel().id());
                client.cancel(topic, data);
            }
        }
    }

}