import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.Charset;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private static final int SERVER_PORT = 31000;
    private static final String CHARSET = "UTF-8";
    private Charset charset = Charset.forName(CHARSET);
    //链接进来的客户端要保存到统一的集合
    public static ChatRoomMap<String, AsynchronousSocketChannel> clients = new ChatRoomMap<>();

    //对服务器进行初始化
    public void init() {
        try {
            //创建线程池
            ExecutorService executorService = Executors.newFixedThreadPool(20);
            //创建channelGroup
            AsynchronousChannelGroup channelGroup
                    = AsynchronousChannelGroup.withThreadPool(executorService);
            //拿到服务器的套接字通道
            AsynchronousServerSocketChannel serverSocketChannel
                    = AsynchronousServerSocketChannel.open(channelGroup);

            //绑定ip和端口
            serverSocketChannel.bind(new InetSocketAddress(SERVER_PORT));
            //循环接收客户端的链接
            serverSocketChannel.accept(null, new AcceptHandler(serverSocketChannel));
        } catch (Exception e) {
            System.out.println("服务器启动失败，可能端口号被占用！");
        }
    }

    private class AcceptHandler implements CompletionHandler<AsynchronousSocketChannel, Object> {
        private AsynchronousServerSocketChannel serverSocketChannel;

        public AcceptHandler(AsynchronousServerSocketChannel serverSocketChannel) {
            this.serverSocketChannel = serverSocketChannel;
        }

        //接收客户端的信息,发送信息的缓冲区
        private ByteBuffer rbuffer = ByteBuffer.allocate(1024);
        private ByteBuffer wbuffer = ByteBuffer.allocate(1024);

        /**
         * 操作系统完成了指定的io后，回调completed
         */
        @Override
        public void completed(AsynchronousSocketChannel clientSocketChannel, Object attachment) {
            //操作系统调用这里的completed方法，表示，服务器有客户端连进来了
            //作系统调用这里的completed方法，表示，服务器有客户端连进来了,递归，又让操作系统给我们准备接下一个客户端
            serverSocketChannel.accept(null, this);
            clientSocketChannel.read(rbuffer, null, new CompletionHandler<Integer, Object>() {
                @Override
                public void completed(Integer hasRead, Object attachment) {
                    //读取数据成功
                    rbuffer.flip();
                    String content = charset.decode(rbuffer).toString(); //StandardCharsets.UTF_8.decode(rbuffer).toString();//Charset.forName("UTF-8").decode(rbuffer).toString();//String.valueOf(Charset.forName("UTF-8").decode(rbuffer));//new String(rbuffer.array(),0,result);
                    //服务器收到客户端的信息，有两类：客户端注册来的用户名；聊天的信息
                    if (content.startsWith(ChatRoomProtocol.USER_ROUND) &&
                            content.endsWith(ChatRoomProtocol.USER_ROUND)) {
                        //信息是注册来的用户名
                        //要进行一系列的处理
                        login(clientSocketChannel, content);
                    } else if (content.startsWith(ChatRoomProtocol.PRIVATEMSG_ROUND) && content.endsWith(ChatRoomProtocol.PRIVATEMSG_ROUND)) {
                        sendMsyToUser(clientSocketChannel, content);
                    } else if (content.startsWith(ChatRoomProtocol.PUBLICMSG_ROUND) && content.endsWith(ChatRoomProtocol.PUBLICMSG_ROUND)) {
                        dispatch(clientSocketChannel, content);
                    }
                    rbuffer.clear();
                    //由来递归实现重复，循环读取数据
                    clientSocketChannel.read(rbuffer, null, this);
                }

                @Override
                public void failed(Throwable ex, Object attachment) {
                    System.out.println("数据读取失败：" + ex);
                    //方可能是客户端关闭了，要把失效的客户端从集合里移除
                    Server.clients.removeByValue(clientSocketChannel);
                }
            });
        }

        /**
         * 操作系统完成了指定的io过程中，出现异常，回调failed
         *
         * @param exc
         * @param attachment
         */
        @Override
        public void failed(Throwable exc, Object attachment) {
            System.out.println("链接失败：" + exc);
        }

        /**
         * 服务器实现客户端登录功能
         *
         * @param client
         * @param content
         */
        private void login(AsynchronousSocketChannel client, String content) {
            System.out.println("登录来啦....");
            try {
                //接受到的是用户名称
                //拿到真正的用户名称
                String userName = getRealMsg(content);
                //判断用户不能重复
                if (Server.clients.map.containsKey(userName)) {
                    System.out.println("用户名重复了");
                    wbuffer.clear();
                    wbuffer.put(charset.encode(ChatRoomProtocol.USER_ROUND + ChatRoomProtocol.NAME_REP
                            + ChatRoomProtocol.USER_ROUND));
                    wbuffer.flip();
                    client.write(wbuffer).get();
                } else {
                    System.out.println("用户登录成功！");
                    wbuffer.clear();
                    wbuffer.put(charset.encode(ChatRoomProtocol.USER_ROUND + ChatRoomProtocol.LOGIN_SUCCESS
                            + ChatRoomProtocol.USER_ROUND));
                    wbuffer.flip();
                    client.write(wbuffer).get();
                    Server.clients.put(userName, client);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /**
         * 对私聊信息的转发
         *
         * @param client
         * @param str
         */
        private void sendMsyToUser(AsynchronousSocketChannel client, String str) {
            try {
                //客户端发送来的信息是私聊
                //拿到真正的信息,信息里包含了目标用户和消息
                String userAndMsg = getRealMsg(str);
                //上面的信息是用ChatRoomProtocol.SPLIT_SIGN来隔开的
                String targetUser = userAndMsg.split(ChatRoomProtocol.SPLIT_SIGN)[0];
                String privatemsg = userAndMsg.split(ChatRoomProtocol.SPLIT_SIGN)[1];

                //服务器就可以转发给指定的用户了三
                wbuffer.clear();
                wbuffer.put(charset.encode(Server.clients.getKeyByValue(client) + "悄悄地说：" + privatemsg));
                wbuffer.flip();
                Server.clients.map.get(targetUser).write(wbuffer).get();
            } catch (Exception e) {
                Server.clients.removeByValue(client);
            }
        }

        /**
         * 对公聊信息的广播
         *
         * @param client
         * @param str
         */
        private void dispatch(AsynchronousSocketChannel client, String str) {
            try {
                //拿到真正的信息
                String publicmsg = getRealMsg(str);
                Set<AsynchronousSocketChannel> valueSet = Server.clients.getValueSet();
                for (AsynchronousSocketChannel cli : valueSet) {
                    wbuffer.clear();
                    wbuffer.put(charset.encode(Server.clients.getKeyByValue(client) + "说：" + publicmsg));
                    wbuffer.flip();
                    cli.write(wbuffer).get();
                }
            } catch (Exception e) {
                Server.clients.removeByValue(client);
            }
        }

        //去除协议字符的方法
        private String getRealMsg(String lines) {
            return lines.substring(ChatRoomProtocol.PROTOCOL_LEN, lines.length() - ChatRoomProtocol.PROTOCOL_LEN);
        }
    }

    //服务器的程序执行入口
    public static void main(String[] args) {
        Server server = new Server();
        server.init();
        try {
            Thread.sleep(100000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}