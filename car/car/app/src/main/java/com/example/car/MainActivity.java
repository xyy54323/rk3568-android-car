package com.example.car;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    // UI组件
    private TextView directionTextView;
    private TextView speedTextView;
    private TextView angleTextView;
    private EditText ipEditText;
    private EditText portEditText;
    private Button connectButton;

    // 网络参数
    private String serverIp;
    private int port;

    // 网络连接
    private Socket clientSocket;
    private PrintWriter writer;

    // 线程管理
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Object connectionLock = new Object();

    // 状态标志（使用原子类型保证线程安全）
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final AtomicBoolean isSending = new AtomicBoolean(false);
    private long lastSendTime = 0;

    /**
     * 主活动的创建方法
     * 在活动创建时被调用，用于初始化活动的用户界面和一些必要的组件
     *
     * @param savedInstanceState 如果活动以前被创建过，这个状态会被保存下来作为参数传入
     *                           用于恢复以前的状态，如果没有以前的状态则为null
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this); // 启用EdgeToEdge模式，使内容区域扩展到屏幕边缘
        setContentView(R.layout.activity_main); // 设置活动的布局资源文件

        initViews(); // 初始化视图组件
        setupJoystickListener(); // 设置虚拟摇杆的监听器
        setupWindowInsets(); // 设置窗口的内边距
    }

    /**
     * 初始化界面控件
     *
     * 此方法将查找并初始化布局文件中的各个控件，包括摇杆视图、文本视图、编辑文本和按钮
     * 它还设置了连接按钮的点击事件监听器，以处理连接点击事件
     */
    private void initViews() {
        // 查找并初始化摇杆视图
        JoystickView joystick = findViewById(R.id.joystick);
        // 查找并初始化显示方向的文本视图
        directionTextView = findViewById(R.id.direction);
        // 查找并初始化显示速度比率的文本视图
        speedTextView = findViewById(R.id.SpeedRatio);
        // 查找并初始化显示角度的文本视图
        angleTextView = findViewById(R.id.angle);
        // 查找并初始化输入服务器IP的编辑文本
        ipEditText = findViewById(R.id.serverIp);
        // 查找并初始化输入端口的编辑文本
        portEditText = findViewById(R.id.port);
        // 查找并初始化连接按钮
        connectButton = findViewById(R.id.connect);

        // 为连接按钮设置点击事件监听器，处理连接点击事件
        connectButton.setOnClickListener(v -> handleConnectClick());
    }

    /**
     * 处理连接或断开连接点击事件
     * 此方法旨在验证用户输入的IP和端口，并尝试连接到指定的服务器，或断开当前的连接
     */
    private void handleConnectClick() {
        if (isConnected.get()) {
            // 如果已经连接，则断开连接
            disconnectFromServer();
        } else {
            // 如果未连接，则尝试连接到服务器
            // 获取并修剪IP输入框中的内容
            String ip = ipEditText.getText().toString().trim();
            // 获取并修剪端口输入框中的内容
            String portStr = portEditText.getText().toString().trim();

            // 检查IP和端口是否都已输入，如果任一为空，则提示用户并返回
            if (ip.isEmpty() || portStr.isEmpty()) {
                showToast("请输入IP和端口");
                return;
            }

            // 验证IP地址格式，如果不正确，则提示用户并返回
            if (!isValidIp(ip)) {
                showToast("IP地址格式错误！");
                return;
            }

            try {
                // 将端口字符串转换为整数
                int port = Integer.parseInt(portStr);
                // 检查端口号是否在有效范围内（1到65535），如果不在，则提示用户并返回
                if (port < 1 || port > 65535) {
                    showToast("端口不存在！");
                    return;
                }

                // 如果IP和端口都有效，则设置服务器IP和端口为输入的值
                this.serverIp = ip;
                this.port = port;
                // 禁用连接按钮以防止重复点击
                setConnectButtonState(false);
                // 尝试连接到服务器
                connectToServer();
                // 显示连接中的提示信息
                showToast("连接中...");
            } catch (NumberFormatException e) {
                // 如果端口转换为整数时发生错误，则提示用户
                showToast("端口错误！");
            }
        }
    }

    /**
     * 设置摇杆监听器
     * 本方法用于初始化摇杆视图，并设置方向变化监听器，以便在摇杆方向改变时进行相应处理
     */
    private void setupJoystickListener() {
        // 获取摇杆视图实例
        JoystickView joystick = findViewById(R.id.joystick);
        // 为摇杆视图设置方向变化监听器，处理摇杆运动
        joystick.setOnDirectionChangeListener(this::handleJoystickMovement);
    }

 // 添加一个新的线程来处理持续发送0, 0数据
private Thread stopSendingThread;

/**
 * 处理摇杆的移动事件
 * 此方法根据摇杆的移动来计算和记录方向、速度和角度，并更新显示如果设备已连接，则发送控制数据
 *
 * @param direction 摇杆的方向，使用JoystickView.Direction枚举类型
 * @param speedRatio 速度比率，表示当前速度与最大速度的比例，浮点数
 * @param angle 摇杆的角度，双精度浮点数
 */
private void handleJoystickMovement(JoystickView.Direction direction,
                                    float speedRatio, double angle) {
    // 记录摇杆的当前状态：方向、速度比率和角度
    Log.d("Joystick", String.format(Locale.US,
            "方向: %s 速度: %.2f 角度: %.1f°",
            direction, speedRatio, angle));

    // 更新显示方向、速度比率和角度
    updateDisplay(direction, speedRatio, angle);

    // 如果设备已连接，发送角度和速度比率数据
    if (isConnected.get()) {
        if (direction == JoystickView.Direction.NONE) {
            // 如果方向为停止状态，启动发送0, 0数据的线程
            if (stopSendingThread == null || !stopSendingThread.isAlive()) {
                stopSendingThread = new Thread(() -> {
                    while (isConnected.get() && isStopState()) {
                        sendData(0, 0);
                        try {
                            Thread.sleep(50); // 每50ms发送一次
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                });
                stopSendingThread.start();
            }
        } else {
            // 如果方向不再是停止状态，停止发送0, 0数据的线程
            if (stopSendingThread != null && stopSendingThread.isAlive()) {
                stopSendingThread.interrupt();
                stopSendingThread = null;
            }
            sendData(angle, speedRatio * 100);
        }
    }
}

// 添加一个方法来检查当前是否为停止状态
private boolean isStopState() {
    JoystickView joystick = findViewById(R.id.joystick);
    return joystick.getCurrentDirection() == JoystickView.Direction.NONE;
}



    /**
     * 更新显示信息，包括方向、速度和角度
     * 此方法使用运行于UI线程，以确保界面的正确更新
     *
     * @param direction JoystickView.Direction类型，表示当前的方向
     * @param speedRatio 浮点数，表示当前的速度比例，范围为0到1
     * @param angle 双精度浮点数，表示当前的角度，单位为度
     */
    private void updateDisplay(JoystickView.Direction direction,
                               float speedRatio, double angle) {
        runOnUiThread(() -> {
            // 更新方向文本并设置其透明度
            directionTextView.setText(getDirectionText(direction));
            directionTextView.setAlpha(0.8f);


            // 更新速度文本并设置其颜色
            speedTextView.setText(String.format(Locale.CHINA,
                    "当前速度: %.0f%%", speedRatio * 100));
            speedTextView.setTextColor(getSpeedColor(speedRatio));

            // 更新角度文本并设置其透明度
            angleTextView.setText(String.format(Locale.CHINA,
                    "当前角度: %.1f°", angle));
            angleTextView.setAlpha(0.8f);
        });


    }

    // region 网络连接管理
    /**
     * 连接到服务器的方法
     * 该方法在一个单独的线程中执行，以避免网络操作阻塞主线程
     * 使用同步块来确保连接的互斥性，防止多个线程同时修改连接状态
     */
    private void connectToServer() {
        executor.execute(() -> {
            synchronized (connectionLock) {
                try {
                    // 在尝试新连接之前，关闭任何现有的连接
                    closeConnection();

                    // 创建到服务器的新Socket连接
                    clientSocket = new Socket(serverIp, port);
                    // 设置Socket读取超时时间为3秒
                    clientSocket.setSoTimeout(3000);
                    // 初始化用于向服务器发送数据的PrintWriter
                    writer = new PrintWriter(clientSocket.getOutputStream(), true);

                    // 更新应用内部的连接状态并显示连接成功的通知
                    updateConnectionStatus(true);
                    showToast("连接成功");
                    //发送数据,更新服务端显示
                    sendData(0, 0);
                } catch (IOException e) {
                    // 如果连接失败，更新连接状态并显示错误信息
                    updateConnectionStatus(false);
                    showToast("连接失败: " + e.getMessage());
                } finally {
                    // 重新启用连接按钮，无论连接成功还是失败
                    setConnectButtonState(true);
                    // 通知其他等待连接锁的线程
                    connectionLock.notifyAll();
                }
            }
        });
    }

    /**
     * 断开与服务器的连接
     * 该方法在一个单独的线程中执行，以避免网络操作阻塞主线程
     * 使用同步块来确保连接的互斥性，防止多个线程同时修改连接状态
     */
    private void disconnectFromServer() {
        executor.execute(() -> {
            synchronized (connectionLock) {
                try {
                    // 关闭当前的连接
                    closeConnection();

                    // 更新应用内部的连接状态并显示断开连接的通知
                    updateConnectionStatus(false);
                    showToast("已断开连接");
                } catch (Exception e) {
                    // 如果断开连接时发生错误，更新连接状态并显示错误信息
                    updateConnectionStatus(false);
                    showToast("断开连接失败: " + e.getMessage());
                } finally {
                    // 重新启用连接按钮
                    setConnectButtonState(true);
                    // 通知其他等待连接锁的线程
                    connectionLock.notifyAll();
                }
            }
        });
    }

    /**
     * 发送数据到远程服务器
     * 该方法主要负责将角度和速度信息打包成特定格式的字符串，并通过网络发送出去
     *
     * @param angle 角度信息，表示某个方向或位置
     * @param speed 速度信息，表示移动的快慢
     */
    private void sendData(double angle, float speed) {
        // 发送频率限制（50ms）
        if (System.currentTimeMillis() - lastSendTime < 50) return;
        lastSendTime = System.currentTimeMillis();

        // 避免重复发送
        if (isSending.get()) return;
        isSending.set(true);

        // 使用线程池执行发送任务，避免阻塞当前线程
        executor.execute(() -> {
            synchronized (connectionLock) {
                try {
                    // 确保连接有效，否则尝试重新连接
                    if (!isConnectionValid()) {
                        connectToServer();
                        connectionLock.wait(1000);
                    }

                    // 发送数据
                    if (writer != null) {
                        String message = String.format(Locale.US,
                                "Angle: %.0f, Speed: %.0f", angle, speed);
                        writer.println(message);
                        //打印log
                        Log.d("MainActivity", "Sent: " + message);
                    }
                } catch (Exception e) {
                    // 异常处理
                    handleSendError(e);
                } finally {
                    // 重置发送状态标志
                    isSending.set(false);
                }
            }
        });
    }
    // endregion

    // region 工具方法
    /**
     * 处理发送错误的方法
     * 当发送操作失败时调用此方法，它执行以下操作：
     * 1. 显示一个toast消息，提示发送失败的原因（异常消息）
     * 2. 关闭连接
     * 3. 更新连接状态为未连接
     *
     * @param e 异常对象，包含发送失败的原因
     */
    private void handleSendError(Exception e) {
        showToast("发送失败: " + e.getMessage());
        closeConnection();
        updateConnectionStatus(false);
    }

    /**
     * 检查当前客户端套接字的连接是否有效
     *
     * 此方法通过一系列条件判断来确定客户端与服务器之间的连接状态是否正常
     * 它首先确保clientSocket对象不为空，以防止空指针异常
     * 接着检查套接字是否已连接，且没有被关闭，以及输入输出流是否都处于正常工作状态
     * 只有当所有这些条件都满足时，才认为连接是有效的
     *
     * @return boolean 表示连接是否有效的布尔值如果连接有效则返回true，否则返回false
     */
    private boolean isConnectionValid() {
        return clientSocket != null
                && clientSocket.isConnected()
                && !clientSocket.isClosed()
                && !clientSocket.isInputShutdown()
                && !clientSocket.isOutputShutdown();
    }

    /**
     * 关闭连接资源
     * 此方法旨在确保在不再需要时正确关闭连接和流，以防止资源泄露和潜在的连接问题
     */
    private void closeConnection() {
        try {
            //发送数据0
            sendData(0, 0);
            // 关闭输出流
            if (writer != null) {
                writer.close();
                writer = null;
            }
            // 关闭客户端套接字
            if (clientSocket != null) {
                clientSocket.close();
                clientSocket = null;
            }
        } catch (IOException e) {
            // 异常处理：记录关闭连接时发生的错误
            Log.e("Network", "关闭连接时出错", e);
        }
    }

    /**
     * 更新应用界面的连接状态
     * 此方法根据连接状态更新应用界面，包括连接按钮的文本和背景颜色
     *
     * @param connected 一个布尔值，表示当前的连接状态 true表示已连接，false表示未连接
     */
    private void updateConnectionStatus(boolean connected) {
        // 更新内部状态变量以反映当前的连接状态
        isConnected.set(connected);

        // 在主线程中运行UI更新操作，以确保线程安全
        runOnUiThread(() -> {
            // 根据连接状态更新连接按钮的文本
            connectButton.setText(connected ? "断开连接" : "连接");

            // 根据连接状态更新连接按钮的背景颜色
            connectButton.setBackgroundColor(connected ? Color.GREEN : Color.RED);
        });
    }

    /**
     * 设置连接按钮的状态
     * 此方法通过在主线程中运行来更新UI，确保按钮的状态更改在主线程中执行，
     * 以避免多线程环境下对UI组件进行操作时可能出现的问题
     *
     * @param enabled 指定按钮是否启用 true表示按钮将被启用，false表示按钮将被禁用
     */
    private void setConnectButtonState(boolean enabled) {
        runOnUiThread(() -> connectButton.setEnabled(enabled));
    }
    // endregion

    // region 其他基础方法
    /**
     * 根据摇杆的方向获取对应的文本描述
     *
     * @param direction 摇杆的方向，属于 JoystickView.Direction 枚举类型
     * @return 返回对应方向的文本描述
     */
    private String getDirectionText(JoystickView.Direction direction) {
        switch (direction) {
            case UP: return "↑ 正前方";
            case RIGHT: return "→ 正右方";
            case DOWN: return "↓ 正后方";
            case LEFT: return "← 正左方";
            case UP_RIGHT: return "↗ 右前方";
            case DOWN_RIGHT: return "↘ 右后方";
            case DOWN_LEFT: return "↙ 左后方";
            case UP_LEFT: return "↖ 左前方";
            case NONE: return "○ 停止状态";
            default: return "未知方向";
        }
    }

    /**
     * 根据速度比例获取对应的颜色
     * 该方法用于将速度比例转换为HSV颜色空间中的颜色，以反映不同的速度级别
     *
     * @param ratio 速度比例，表示当前速度与最大速度的比值，范围为0到1
     * @return 返回对应速度比例的颜色值，以整数形式表示
     */
    private int getSpeedColor(float ratio) {
        // 根据速度比例计算HSV值，比例乘以120f是为了在HSV颜色轮盘上定位颜色
        float[] hsv = { ratio * 120f, 1f, 1f };
        // 将计算出的HSV值转换为颜色值并返回
        return Color.HSVToColor(hsv);
    }

    /**
     * 在主线程中显示短时间的Toast消息
     *
     * @param text 要显示的Toast消息文本
     */
    private void showToast(String text) {
        handler.post(() -> Toast.makeText(this, text, Toast.LENGTH_SHORT).show());
    }

    /**
     * 验证输入的字符串是否为有效的IPv4地址
     * 该方法使用正则表达式来匹配IPv4地址的格式
     *
     * @param ip 要验证的IP地址字符串
     * @return 如果输入的字符串是有效的IPv4地址，则返回true；否则返回false
     */
    private boolean isValidIp(String ip) {
        // 编译一个正则表达式，用于匹配IPv4地址的格式
        Pattern pattern = Pattern.compile(
                "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");

        // 使用编译的正则表达式创建一个匹配器，并验证输入的IP地址是否匹配IPv4地址的格式
        return pattern.matcher(ip).matches();
    }

    /**
     * 设置窗口嵌入
     * 该方法用于处理窗口嵌入的回调，以便在界面初始化时调整视图的内边距
     * 这是必要的，以确保视图在不同设备和屏幕尺寸下正确显示
     */
    private void setupWindowInsets() {
        // 设置视图以处理窗口嵌入
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            // 获取系统栏的嵌入
            // 这包括状态栏和导航栏等系统UI组件的尺寸
            // 这些信息对于调整视图的内边距至关重要，以避免系统UI组件遮挡内容
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            // 根据系统栏的嵌入调整视图的内边距
            // 这样做是为了确保视图内容不会被系统UI组件遮挡
            // 例如，这可以防止状态栏或导航栏遮挡视图内容
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);

            // 返回原始嵌入对象
            // 这是因为我们已经处理了嵌入，并且不需要进一步的处理
            return insets;
        });
    }

    /**
     * 在活动或组件销毁时执行清理操作
     *
     * 本方法重写自父类，用于确保在组件销毁时，所有资源被正确释放，防止资源泄露
     */
    @Override
    protected void onDestroy() {
        // 关闭执行器，确保所有异步任务被优雅地停止
        executor.shutdown();
        // 关闭与服务器的连接，释放网络资源
        closeConnection();
        // 调用父类的onDestroy方法，执行额外的清理操作
        super.onDestroy();
    }
    // endregion
}