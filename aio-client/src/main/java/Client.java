import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Client {
    private static final int SERVER_PORT = 31000;
    private static final String CHARSET = "UTF-8";
    private Charset charset = Charset.forName(CHARSET);
    //与服务器链接的通道
    AsynchronousSocketChannel clientChannel;
    //定义主窗体
    JFrame mainWin = new JFrame("多人聊天室");
    //定义显示聊天内容的文本域
    JTextArea jta = new JTextArea(16, 48);
    //定义输入聊天内容的文本框
    JTextField jtf = new JTextField(40);
    //定义发送聊天内容的按钮
    JButton sendBtn = new JButton("发送");
    //loing的tip
    String tip = "";
    //写缓冲器
    ByteBuffer wbuffer = ByteBuffer.allocate(1024);
    //读缓冲器
    ByteBuffer rbuffer = ByteBuffer.allocate(1024);


    //上面的皮肤的，整个客户端程序初始化
    public void init() {
        mainWin.setLayout(new BorderLayout());
        jta.setEditable(false);
        mainWin.add(new JScrollPane(jta), BorderLayout.CENTER);
        JPanel jp = new JPanel();
        jp.add(jtf);
        jp.add(sendBtn);
        //按钮我们要给他定义点击后的事件响应
        Action sendAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String content = jtf.getText();
                content = content.trim();
                if (content.length() > 0) {
                    try {
                        if (content.indexOf(":") > 0 && content.startsWith("//")) {
                            //私聊信息
                            content = content.substring(2);
                            wbuffer.clear();
                            wbuffer.put(charset.encode(ChatRoomProtocol.PRIVATEMSG_ROUND +
                                    content.split(":")[0] + ChatRoomProtocol.SPLIT_SIGN +
                                    content.split(":")[1] + ChatRoomProtocol.PRIVATEMSG_ROUND));
                            wbuffer.flip();
                            clientChannel.write(wbuffer).get();
                        } else {
                            //公聊信息
                            wbuffer.clear();
                            wbuffer.put(charset.encode(ChatRoomProtocol.PUBLICMSG_ROUND +
                                    content + ChatRoomProtocol.PUBLICMSG_ROUND));
                            wbuffer.flip();
                            clientChannel.write(wbuffer).get();
                        }
                    } catch (Exception ex) {
                        System.out.println("发送数据异常！");
                    }
                }
                //把发送出去的信息，从文本框中清除
                jtf.setText("");
            }
        };
        //把自己定义的发送信息的事件响应和按钮本身关联
        sendBtn.addActionListener(sendAction);
        //还是定义一个Ctrl+Enter快捷键给发送信息
        jtf.getInputMap().put(KeyStroke.getKeyStroke('\n', InputEvent.CTRL_MASK), "send");
        //上面定义了一个快捷键，把快捷键和发送信息的事件响应关联起来
        jtf.getActionMap().put("send", sendAction);
        mainWin.add(jp, BorderLayout.SOUTH);
        mainWin.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainWin.pack();//把窗体自动调整大小，根据包裹在里面的组件自动调整到合适的大小
        mainWin.setVisible(true);
    }

    //链接服务器
    public void connect() {
        try {
            //定义线程池
            ExecutorService executorService =
                    Executors.newFixedThreadPool(80);
            //定义channelGroup
            AsynchronousChannelGroup channelGroup =
                    AsynchronousChannelGroup.withThreadPool(executorService);
            //获取客户端的套接字通道,因为我们用了GUI，客户端套接字应该保存到上面的属性上去
            clientChannel =
                    AsynchronousSocketChannel.open(channelGroup);
            //链接服务器
            clientChannel.connect(new InetSocketAddress("127.0.0.1", SERVER_PORT)).get();
            System.out.println("客户端链接服务器成功！");
            //链接上服务器后，就要登录服务器
            login(clientChannel, tip);

            //接收服务器传来的信息
            rbuffer.clear();
            clientChannel.read(rbuffer, null, new CompletionHandler<Integer, Object>() {
                @Override
                public void completed(Integer result, Object attachment) {
                    //进到这里就表示读出来了
                    rbuffer.flip();
                    String content = charset.decode(rbuffer).toString();
                    //content也是有类型：登录后响应信息，聊天信息
                    if (content.startsWith(ChatRoomProtocol.USER_ROUND) &&
                            content.endsWith(ChatRoomProtocol.USER_ROUND)) {
                        //拿到真正的登录回复信息
                        String loginRes = getRealMsg(content);
                        if (loginRes.equals(ChatRoomProtocol.NAME_REP)) {
                            tip = "用户名重复，请重新";
                            login(clientChannel, tip);
                        } else if (loginRes.equals(ChatRoomProtocol.LOGIN_SUCCESS)) {
                            System.out.println("客户端登录成功！");
                        }
                    } else {
                        //回来的是聊天信息
                        jta.append(content + "\n");
                    }
                    rbuffer.clear();
                    clientChannel.read(rbuffer, null, this);
                }

                @Override
                public void failed(Throwable exc, Object attachment) {
                    System.out.println("读取数据失败" + exc);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //去除协议字符的方法
    private String getRealMsg(String lines) {
        return lines.substring(ChatRoomProtocol.PROTOCOL_LEN, lines.length() - ChatRoomProtocol.PROTOCOL_LEN);
    }


    private void login(AsynchronousSocketChannel client, String tip) {
        try {
            //虽然我们还没见过到GUI，这里小小用一个gui里的弹出对话框
            String userName = JOptionPane.showInputDialog(tip + "输入用户名：");
            //把userName发送到服务器上去
            wbuffer.clear();
            wbuffer.put(charset.encode(ChatRoomProtocol.USER_ROUND + userName + ChatRoomProtocol.USER_ROUND));
            wbuffer.flip();
            client.write(wbuffer).get();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //客户端的程序入口
    public static void main(String[] args) {
        Client client = new Client();
        client.init();
        client.connect();
    }
}