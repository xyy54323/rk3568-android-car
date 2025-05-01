package com.example.car;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class JoystickView extends View {
    private Paint backgroundPaint;
    private Paint handlePaint;
    private float centerX, centerY;
    private float handleRadius;
    private float backgroundRadius;
    private float handleX, handleY;
    private Direction currentDirection = Direction.NONE;
    private float currentSpeedRatio = 0f;
    private double currentAngle;

    public enum Direction {
        NONE, UP, DOWN, LEFT, RIGHT, UP_LEFT, UP_RIGHT, DOWN_LEFT, DOWN_RIGHT
    }

    public interface OnDirectionChangeListener {
        void onDirectionChanged(Direction direction, float speedRatio, double angle);
    }

    private OnDirectionChangeListener listener;

    public JoystickView(Context context) {
        super(context);
        init();
    }

    public JoystickView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.GRAY);
        backgroundPaint.setAlpha(100);

        handlePaint = new Paint();
        handlePaint.setColor(Color.BLUE);
        handlePaint.setAlpha(150);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
        handleX = centerX;
        handleY = centerY;
        backgroundRadius = Math.min(w, h) / 3f;
        handleRadius = backgroundRadius / 3f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawCircle(centerX, centerY, backgroundRadius, backgroundPaint);
        canvas.drawCircle(handleX, handleY, handleRadius, handlePaint);
    }

    /**
     * 处理触摸屏事件的方法
     * 该方法主要用于处理用户对虚拟摇杆的触摸操作，包括按下、移动和释放等动作
     *
     * @param event 触摸事件，包含触摸的位置信息和动作类型
     * @return 始终返回true，表示所有事件都已消费
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 获取触摸位置的X坐标
        float touchX = event.getX();
        // 获取触摸位置的Y坐标
        float touchY = event.getY();

        // 根据触摸事件的动作类型，执行相应的逻辑
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                // 计算触摸位置相对于摇杆中心的偏移量
                float deltaX = touchX - centerX;
                float deltaY = touchY - centerY;
                // 计算触摸位置到摇杆中心的距离
                float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);

                // 如果距离超过背景圆的半径，将触摸位置限制在背景圆内
                if (distance > backgroundRadius) {
                    float ratio = backgroundRadius / distance;
                    deltaX *= ratio;
                    deltaY *= ratio;
                }

                // 更新摇杆手柄的位置
                handleX = centerX + deltaX;
                handleY = centerY + deltaY;
                // 根据触摸位置计算摇杆的移动方向
                calculateDirection(deltaX, deltaY);

                // 如果有监听器，通知监听器摇杆方向变化
                if (listener != null) {
                    listener.onDirectionChanged(currentDirection, currentSpeedRatio, currentAngle);
                }

                // 重绘界面以反映摇杆位置的变化
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                // 当触摸释放时，重置摇杆状态
                handleX = centerX;
                handleY = centerY;
                currentDirection = Direction.NONE;
                currentSpeedRatio = 0f;
                currentAngle = 0;
                // 如果有监听器，通知监听器摇杆已静止
                if (listener != null) {
                    listener.onDirectionChanged(Direction.NONE, 0f, currentAngle);
                }
                // 重绘界面以反映摇杆状态的重置
                invalidate();
                break;
        }
        // 表示所有事件都已消费
        return true;
    }

    /**
     * 计算触摸移动的方向（0度指向正前方）
     *
     * @param deltaX 水平偏移量（右为正）
     * @param deltaY 垂直偏移量（下为正）
     */
    private void calculateDirection(float deltaX, float deltaY) {
        // 计算移动距离和速度比率
        float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        currentSpeedRatio = Math.min(distance / backgroundRadius, 1.0f);

        // 计算以正前方为0度的角度（顺时针0-360）
        currentAngle = (Math.toDegrees(Math.atan2(-deltaX, -deltaY)) + 360) % 360;

        // 根据新角度划分方向区间
        Direction newDirection = Direction.NONE;
        if (currentAngle >= 337.5 || currentAngle < 22.5) {    // 正前方
            newDirection = Direction.UP;
        } else if (currentAngle >= 22.5 && currentAngle < 67.5) {  // 左前方
            newDirection = Direction.UP_LEFT;
        } else if (currentAngle >= 67.5 && currentAngle < 112.5) { // 正左方
            newDirection = Direction.LEFT;
        } else if (currentAngle >= 112.5 && currentAngle < 157.5) { // 左后方
            newDirection = Direction.DOWN_LEFT;
        } else if (currentAngle >= 157.5 && currentAngle < 202.5) { // 正后方
            newDirection = Direction.DOWN;
        } else if (currentAngle >= 202.5 && currentAngle < 247.5) { // 右后方
            newDirection = Direction.DOWN_RIGHT;
        } else if (currentAngle >= 247.5 && currentAngle < 292.5) { // 正右方
            newDirection = Direction.RIGHT;
        } else { // 292.5-337.5 右前方
            newDirection = Direction.UP_RIGHT;
        }

        // 调整对角线方向灵敏度
        if (Math.abs(deltaX) > backgroundRadius * 0.4 &&
            Math.abs(deltaY) > backgroundRadius * 0.4) {
            if (newDirection == Direction.UP) {
                newDirection = (deltaX > 0) ? Direction.UP_RIGHT : Direction.UP_LEFT;
            } else if (newDirection == Direction.DOWN) {
                newDirection = (deltaX > 0) ? Direction.DOWN_RIGHT : Direction.DOWN_LEFT;
            }
        }

        triggerDirectionChange(newDirection, currentAngle);
    }

    private void triggerDirectionChange(Direction newDirection, double angle) {
        currentDirection = newDirection;
        if (listener != null) {
            listener.onDirectionChanged(currentDirection, currentSpeedRatio, angle);
        }
    }

    public void setOnDirectionChangeListener(OnDirectionChangeListener listener) {
        this.listener = listener;
    }

    /**
    * 获取当前摇杆的速度比例（0.0-1.0）
    * 当摇杆处于中心时为0，推到最大半径时为1.0
    *
    * @return 当前速度比例值（浮点数）
    */
    public float getCurrentSpeedRatio() {
        return currentSpeedRatio;
    }

    /**
    * 获取当前摇杆的实时角度（0-360度）
    * 角度以正前方为0度，顺时针方向递增
    *
    * @return 当前角度值（双精度浮点数）
    */
    public double getCurrentAngle() {
        return currentAngle;
    }

    // 获取当前摇杆的方向
    public Direction getCurrentDirection() {
        return currentDirection;
    }

    /**
    * 获取X轴方向比例（-1.0到1.0）
    * 左为负，右为正
    */
    public float getXratio() {
        return (handleX - centerX) / backgroundRadius;
    }

    /**
    * 获取Y轴方向比例（-1.0到1.0）
    * 上为负，下为正（符合屏幕坐标系）
    */
    public float getYratio() {
        return (handleX - centerY) / backgroundRadius;
    }



}
