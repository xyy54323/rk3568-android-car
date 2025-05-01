package com.example.carjni;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.example.carjni.databinding.ActivityMainBinding;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class MainActivity extends AppCompatActivity {

    // Used to load the 'carjni' library on application startup.
    static {
        System.loadLibrary("carjni");
    }

    private ActivityMainBinding binding;
    private TextView speedTextView;
    private TextView angleTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 初始化视图组件
        speedTextView = findViewById(R.id.SpeedRatio);
        angleTextView = findViewById(R.id.angle);

        // 启动服务器
        startServer();
        // 打开设备节点
        MyDeviceOpen();
    }

    /**
     * 启动服务器
     * 该方法在一个新的线程中启动一个服务器，监听指定端口，并接受客户端连接请求
     * 选择在新线程中运行是因为服务器通常需要长时间运行，且处理客户端请求时不应阻塞主线程
     * 使用ServerSocket来监听8888端口，这是服务器与客户端通信的入口
     * 该方法不接受参数，也无返回值
     */
    private void startServer() {
        new Thread(() -> {
            try {
                // 创建ServerSocket实例，指定监听的端口号为8888
                ServerSocket serverSocket = new ServerSocket(8888);
                // 循环等待并接受客户端的连接请求
                while (!Thread.interrupted()) {
                    // 接受客户端连接请求，创建Socket对象处理与客户端的通信
                    Socket client = serverSocket.accept();
                    // 处理客户端请求，具体逻辑由handleClient方法实现
                    handleClient(client);
                }
            } catch (IOException e) {
                // 输出异常信息，以便于调试和日志记录
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * 处理客户端连接
     * 该方法负责读取客户端发送的数据，解析并更新UI及控制设备
     *
     * @param client 套接字对象，代表与客户端的连接
     */
    private void handleClient(Socket client) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Log.d("Network", "收到原始数据: " + line);
                String[] parts = line.split(": |, ");

                if (parts.length != 4) {
                    Log.w("Network", "数据格式错误，跳过处理");
                    continue;
                }

                try {
                    final int angle = Integer.parseInt(parts[1].trim());
                    final int speed = Integer.parseInt(parts[3].trim());
                    final int finalAngle = (speed == 0) ? 0 : angle;

                    runOnUiThread(() -> {
                        // 使用安全格式化
                        String angleText = getString(R.string.angle_text, finalAngle);
                        String speedText = getString(R.string.speed_text, speed);

                        angleTextView.setText(angleText);
                        speedTextView.setText(speedText);
                        controlCar(finalAngle, speed);
                    });
                } catch (NumberFormatException e) {
                    Log.e("Network", "数值解析失败: " + line);
                }
            }
        } catch (IOException e) {
            Log.e("Network", "连接异常: " + e.getMessage());
        } finally {
            try {
                client.close();
            } catch (IOException e) {
                Log.e("Network", "关闭连接失败");
            }
        }
    }

    // 添加JNI方法声明
    public native void controlCar(int angle, int speed);
    public native int MyDeviceOpen();
    public native int MyDeviceClose();
}
