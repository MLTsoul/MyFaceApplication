package com.android.mlt.myfaceapplication;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.SurfaceHolder;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't
 * shown. On devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient
 * mode. The watch face is drawn with less contrast in mute mode.
 * <p>
 * Important Note: Because watch face apps do not have a default Activity in
 * their project, you will need to set your Configurations to
 * "Do not launch Activity" for both the Wear and/or Application modules. If you
 * are unsure how to do this, please review the "Run Starter project" section
 * in the Google Watch Face Code Lab:
 * https://codelabs.developers.google.com/codelabs/watchface/index.html#0
 */
public class MyWatchFace extends CanvasWatchFaceService {

    /*
     * Updates rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                if (msg.what == MSG_UPDATE_TIME) {
                    engine.handleUpdateTimeMessage();
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        private static final float HOUR_STROKE_WIDTH = 5f;
        private static final float MINUTE_STROKE_WIDTH = 3f;
        private static final float SECOND_TICK_STROKE_WIDTH = 2f;

        private static final float CENTER_GAP_AND_CIRCLE_RADIUS = 4f;

        private static final int SHADOW_RADIUS = 4;
        /* Handler to update the time once a second in interactive mode. */
        private final Handler mUpdateTimeHandler = new EngineHandler(this);
        private Calendar mCalendar;
        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        private boolean mRegisteredTimeZoneReceiver = false;
        private boolean mMuteMode;
        private float mCenterX;
        private float mCenterY;
        private float mSecondHandLength;
        private float sMinuteHandLength;
        private float sHourHandLength;
        /* Colors for all hands (hour, minute, seconds, ticks) based on photo loaded. */
        private int mWatchHandColor;
        private int mWatchTickColor;
        private int mWatchHandHighlightColor;
        private int mWatchHandShadowColor;
        private Paint mDatePaint;
        private Paint mHourPaint;
        private Paint mMinutePaint;
        private Paint mSecondPaint;
        private Paint mTickAndCirclePaint;
        private Paint mBackgroundPaint;
        private boolean mAmbient;
        private int dateTextSize = 26;
        BatteryManager manager;


        //        float innerTickRadius;
//        float outerTickRadius;
        // 长刻度停止位置
        int LONG_TICK_STOP = 12;
        // 短刻度开始位置
        int SHORT_TICK_START = 14;
        // 短刻度停止位置
        int SHORT_TICK_STOP = 16;

        // 指针模式变量 s
        // 做为对比日期
        int oldYear;
        int oldMonth;
        int oldDay;
        // 此刻日期
        int year;
        int month;
        int day;
        // 此刻周几
        int dayWeek;
        // 日期文字宽度
        int dataTextWidth;
        // 日期文字高度
        int dataTextHeight;
        // 日期文字
        String dataText;
        // 日期星期拼接
        StringBuilder timeSb = new StringBuilder();
        // 电量拼接
        StringBuilder electric = new StringBuilder();
        // 电量
        String electricStr;
        // 未读通知拼接
        StringBuilder notice = new StringBuilder();
        // 未读通知
        String noticeStr;
        // 通知条数
        int noticeNumber = 0;
        // 刻度个数
        int TICK_SIZE = 60;
        // 绘制刻度时每次旋转角度
        float tickRot = 360f / TICK_SIZE;
        // 指针模式变量 e

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setAcceptsTapEvents(true)
                    // 隐藏系统未读通知数量
                    .setHideNotificationIndicator(true)
                    // 未读通知数量在状态栏显示
                    .setShowUnreadCountIndicator(true)
                    .build());
            // 获取时间对象，每次绘制图像
            mCalendar = Calendar.getInstance();
            // 管理
            manager = (BatteryManager) getSystemService(BATTERY_SERVICE);
            // 初始化背景数据
            initializeBackground();
            // 初始化表盘数据
            initializeWatchFace();
        }

        private void initializeBackground() {
            // 初始化数字背景画笔
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(Color.WHITE);
        }

        private void initializeWatchFace() {
            /* Set defaults for colors */
            mWatchHandColor = Color.argb(80, 255, 255, 255);
            mWatchTickColor = Color.argb(255, 255, 255, 255);
            mWatchHandHighlightColor = Color.LTGRAY;
            mWatchHandShadowColor = Color.BLACK;

            mDatePaint = new Paint();
            mDatePaint.setColor(mWatchHandHighlightColor);
            mDatePaint.setStrokeWidth(HOUR_STROKE_WIDTH);
            mDatePaint.setAntiAlias(true);
            mDatePaint.setStrokeCap(Paint.Cap.ROUND);
            mDatePaint.setTextSize(dateTextSize);

            mHourPaint = new Paint();
            mHourPaint.setColor(mWatchHandHighlightColor);
            mHourPaint.setStrokeWidth(HOUR_STROKE_WIDTH);
            mHourPaint.setAntiAlias(true);
            mHourPaint.setStrokeCap(Paint.Cap.ROUND);
            mHourPaint.setTextSize(dateTextSize - 2);
            // 设置阴影
//            mHourPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mMinutePaint = new Paint();
            mMinutePaint.setColor(mWatchHandHighlightColor);
            mMinutePaint.setStrokeWidth(MINUTE_STROKE_WIDTH);
            mMinutePaint.setAntiAlias(true);
            mMinutePaint.setStrokeCap(Paint.Cap.ROUND);
            mMinutePaint.setTextSize(dateTextSize - 4);
            // 设置阴影
//            mMinutePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mSecondPaint = new Paint();
            mSecondPaint.setColor(mWatchHandColor);
            mSecondPaint.setStrokeWidth(SECOND_TICK_STROKE_WIDTH);
            mSecondPaint.setAntiAlias(true);
            mSecondPaint.setStrokeCap(Paint.Cap.ROUND);
            // 设置阴影
//            mSecondPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mTickAndCirclePaint = new Paint();
            mTickAndCirclePaint.setColor(mWatchTickColor);
            mTickAndCirclePaint.setStrokeWidth(SECOND_TICK_STROKE_WIDTH);
            mTickAndCirclePaint.setAntiAlias(true);
            mTickAndCirclePaint.setStyle(Paint.Style.STROKE);
            // 设置阴影
            mTickAndCirclePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        /**
         * 更改微光模式
         *
         * @param inAmbientMode 是否是微光模式
         */
        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            mAmbient = inAmbientMode;

            updateWatchHandStyle();

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        /**
         * 更新数据
         */
        private void updateWatchHandStyle() {
            if (mAmbient) {
                // 微光模式
                mHourPaint.setColor(mWatchHandHighlightColor);
                mMinutePaint.setColor(mWatchHandHighlightColor);
                mSecondPaint.setColor(mWatchHandColor);
                mTickAndCirclePaint.setColor(mWatchTickColor);

                mHourPaint.setAntiAlias(false);
                mMinutePaint.setAntiAlias(false);
                mSecondPaint.setAntiAlias(false);
                mTickAndCirclePaint.setAntiAlias(false);

                mHourPaint.clearShadowLayer();
                mMinutePaint.clearShadowLayer();
                mSecondPaint.clearShadowLayer();
                mTickAndCirclePaint.clearShadowLayer();

            } else {
                mHourPaint.setColor(mWatchHandHighlightColor);
                mMinutePaint.setColor(mWatchHandHighlightColor);
                mSecondPaint.setColor(mWatchHandColor);
                mTickAndCirclePaint.setColor(mWatchTickColor);

                mHourPaint.setAntiAlias(true);
                mMinutePaint.setAntiAlias(true);
                mSecondPaint.setAntiAlias(true);
                mTickAndCirclePaint.setAntiAlias(true);

                // 阴影
                mHourPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mMinutePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mSecondPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mTickAndCirclePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
            }
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            super.onInterruptionFilterChanged(interruptionFilter);
            boolean inMuteMode = (interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE);

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode;
                mHourPaint.setAlpha(inMuteMode ? 100 : 255);
                mMinutePaint.setAlpha(inMuteMode ? 100 : 255);
                mSecondPaint.setAlpha(inMuteMode ? 80 : 255);
                invalidate();
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            mCenterX = width / 2f;
            mCenterY = height / 2f;
            /*
             * Calculate lengths of different hands based on watch screen size.
             */
            mSecondHandLength = (float) (mCenterX * 0.875);
            sMinuteHandLength = (float) (mCenterX * 0.75);
            sHourHandLength = (float) (mCenterX * 0.5);
        }

        /**
         * Captures tap event (and tap type). The {@link WatchFaceService#TAP_TYPE_TAP} case can be
         * used for implementing specific logic to handle the gesture.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // 点击事件
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // 更新绘制时间
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            drawBackground(canvas);
            drawWatchFace(canvas);
        }

        /**
         * 绘制数字表盘
         */
        private void drawWatchFaceNumber(Canvas canvas) {
            int width = canvas.getWidth();
            int height = canvas.getHeight();

            final float seconds =
                    (mCalendar.get(Calendar.SECOND) + mCalendar.get(Calendar.MILLISECOND) / 1000f);
            canvas.drawArc(new RectF(0, 0, width, height), -90, seconds * 6, true, mSecondPaint);
            canvas.drawCircle(mCenterX, mCenterY, mCenterX - 10, mBackgroundPaint);

            // 设置时分秒画笔的字体
            mHourPaint.setTypeface(Typeface.SANS_SERIF);
            mHourPaint.setTextSize(width / 4f);
            mMinutePaint.setTypeface(Typeface.SANS_SERIF);
            mMinutePaint.setTextSize(width / 4f);

            int hour = mCalendar.get(Calendar.HOUR);
            int minute = mCalendar.get(Calendar.MINUTE);
            String hourStr = hour < 10 ? "0" + hour : "" + hour;
            String minuteStr = minute < 10 ? "0" + minute : "" + minute;
            Rect rect = new Rect();
            mHourPaint.getTextBounds(hourStr, 0, hourStr.length(), rect);

            canvas.drawText(
                    hourStr
                    , width / 2f - 10 - mMinutePaint.measureText(hourStr)
                    , height / 2f + (rect.height() / 2f)
                    , mHourPaint
            );
            canvas.drawText(
                    minuteStr
                    , width / 2f + 10
                    , height / 2f + (rect.height() / 2f)
                    , mMinutePaint
            );
        }

        /**
         * 绘制背景
         *
         * @param canvas 画布
         */
        private void drawBackground(Canvas canvas) {
            // 根据是否为微光模式绘制背景
            if (mAmbient) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawColor(Color.argb(255, 30, 30, 30));
            }
            /*
             *使用旋转画布的方式绘制刻度
             */
            // 保存旋转前角度
            canvas.save();
            for (int tickIndex = 0; tickIndex < TICK_SIZE; tickIndex++) {
                if (tickIndex % 5 == 0) {
                    // 每5个刻度绘制一次长刻度
                    canvas.drawLine(mCenterX, 0,
                            mCenterX, LONG_TICK_STOP, mTickAndCirclePaint);
                }
                // 每次旋转一个短刻度角度
                canvas.rotate(tickRot, mCenterX, mCenterY);
                // 绘制短刻度
                canvas.drawLine(mCenterX, SHORT_TICK_START,
                        mCenterX, SHORT_TICK_STOP, mTickAndCirclePaint);
            }
            // 恢复旋转前角度
            canvas.restore();
        }

        /**
         * 指针表盘
         */
        private void drawWatchFace(Canvas canvas) {
            /*
             * 日期和星期
             */
            // 获取此刻日期
            year = mCalendar.get(Calendar.YEAR);
            month = mCalendar.get(Calendar.MONTH) + 1;
            day = mCalendar.get(Calendar.DAY_OF_MONTH);
            // 如果日期与上次相同，不重新计算日期
            if (day != oldDay || month != oldMonth || year != oldYear) {
                oldYear = year;
                oldMonth = month;
                oldDay = day;
                dayWeek = mCalendar.get(Calendar.DAY_OF_WEEK);
                timeSb.delete(0, timeSb.length());
                timeSb.append(month < 10 ? "0" : "").append(month).append("月")
                        .append(day < 10 ? "0" : "").append(day).append("日").append("  ");
                switch (dayWeek) {
                    case 2:
                        timeSb.append("周一");
                        break;
                    case 3:
                        timeSb.append("周二");
                        break;
                    case 4:
                        timeSb.append("周三");
                        break;
                    case 5:
                        timeSb.append("周四");
                        break;
                    case 6:
                        timeSb.append("周五");
                        break;
                    case 7:
                        timeSb.append("周六");
                        break;
                    case 1:
                        timeSb.append("周日");
                        break;
                    default:
                        break;
                }
                dataTextWidth = canvas.getWidth();
                dataTextHeight = canvas.getHeight();
                dataText = timeSb.toString();
            }
            // 绘制日期
            canvas.drawText(dataText
                    , (dataTextWidth - mDatePaint.measureText(dataText)) / 2
                    , (float) (dataTextHeight / 2 - dateTextSize - 20)
                    , mDatePaint
            );

            // 电量
            electric.delete(0, electric.length());
            electric.append(manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)).append("%");
            electricStr = electric.toString();
            canvas.drawText(electricStr
                    , (dataTextWidth - mDatePaint.measureText(electricStr)) / 2
                    , (float) (dataTextHeight / 2 + dateTextSize + 40)
                    , mDatePaint);

            // 获取未读通知数量
            noticeNumber = getUnreadCount();
            if (noticeNumber > 0) {
                // 绘制未读通知数量
                notice.delete(0, notice.length());
                notice.append(noticeNumber).append(" 条新通知");
                noticeStr = notice.toString();
                canvas.drawText(noticeStr
                        , (dataTextWidth - mMinutePaint.measureText(noticeStr)) / 2
                        , (float) (dataTextHeight / 2 + dateTextSize + 80)
                        , mMinutePaint);
            }


            /*
             * 获取时间
             * 360 / 60 = 6 and 360 / 12 = 30.
             */
            final float seconds =
                    (mCalendar.get(Calendar.SECOND) + mCalendar.get(Calendar.MILLISECOND) / 1000f);
            final float secondsRotation = seconds * 6f;

            final float minutesRotation = mCalendar.get(Calendar.MINUTE) * 6f;

            final float hourHandOffset = mCalendar.get(Calendar.MINUTE) / 2f;
            final float hoursRotation = (mCalendar.get(Calendar.HOUR) * 30) + hourHandOffset;

            /*
             * 保存旋转前画布状态
             */
            canvas.save();

            canvas.rotate(hoursRotation, mCenterX, mCenterY);
            canvas.drawLine(
                    mCenterX,
                    mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                    mCenterX,
                    mCenterY - sHourHandLength,
                    mHourPaint);

            canvas.rotate(minutesRotation - hoursRotation, mCenterX, mCenterY);
            canvas.drawLine(
                    mCenterX,
                    mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                    mCenterX,
                    mCenterY - sMinuteHandLength,
                    mMinutePaint);

            /*
             * 根据是否为微光状态绘制秒针
             */
            if (!mAmbient) {
                canvas.rotate(secondsRotation - minutesRotation, mCenterX, mCenterY);
                canvas.drawLine(
                        mCenterX,
                        mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                        mCenterX,
                        mCenterY - mSecondHandLength,
                        mSecondPaint);

            }

            // 绘制表心
            canvas.drawCircle(
                    mCenterX,
                    mCenterY,
                    CENTER_GAP_AND_CIRCLE_RADIUS,
                    mTickAndCirclePaint);

            /*
             * 还原画布状态
             */
            canvas.restore();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                /* Update time zone in case it changed while we weren't visible. */
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts/stops the {@link #mUpdateTimeHandler} timer based on the state of the watch face.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer
         * should only run in active mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !mAmbient;
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
