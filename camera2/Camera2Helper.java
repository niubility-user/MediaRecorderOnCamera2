public class Camera2Helper {
    public static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    public static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;
    private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();
    private static final String TAG = "Camera2Helper";
    private static final int REQUEST_VIDEO_PERMISSIONS = 1;
    public static final int FOCUS_DISAPPEAR = 100;
    private static final String[] VIDEO_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    static {
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    static {
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }

    /**
     * <p>预览View</p>
     * An {@link AutoFitTextureView} for camera preview.
     */
    private AutoFitTextureView mAutoFitTextureView;

    /**
     * <p>相机设备</p>
     * A reference to the opened {@link CameraDevice}.
     */
    private CameraDevice mCameraDevice;

    /**
     * <p>请求的Session</p>
     * A reference to the current {@link CameraCaptureSession} for
     * preview.
     */
    private CameraCaptureSession mCameraCaptureSession;

    /**
     * <p>预览View的Surface</p>
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        /**
         * 可用时才能打开相机预览画面
         * @param width surfaceTextured的宽
         * @param height surfaceTexture的高
         */
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture,
                                              int width, int height) {
            openCamera(width, height);
        }

        /**
         * 大小改变时需要重新设置TextureView的Matrix参数
         * @param width surfaceTextured的宽
         * @param height surfaceTexture的高
         */
        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture,
                                                int width, int height) {
            configureTransform(width, height);
        }

        /**
         * surfaceTexture销毁时调用
         */
        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            LogUtil.getInstance().d(TAG, "SurfaceTexture被销毁");
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        }
    };

    /**
     * <p>相机设备的状态回调</p>
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its status.
     */
    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            if (mMediaRecorder == null) {
                mMediaRecorder = new MediaRecorder();
            }
            readyToPreview();
            mCameraOpenCloseLock.release();
            if (null != mAutoFitTextureView) {
                configureTransform(mAutoFitTextureView.getWidth(), mAutoFitTextureView.getHeight());
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            Activity activity = mActivity;
            if (null != activity) {
                activity.finish();
            }
        }

    };
    private PreviewSessionCallback mPreviewSessionCallback;

    /**
     * <p>预览的尺寸</p>
     * The {@link Size} of camera preview.
     */
    private Size mPreviewSize;

    /**
     * <p>MediaRecorder的录像尺寸</p>
     * The {@link Size} of video recording.
     */
    private Size mVideoSize;

    /**
     * <p>MediaRecorder对象</p>
     */
    private MediaRecorder mMediaRecorder;

    /**
     * <p>判断是否正在录像</p>
     * Whether the app is recording video now
     */
    private boolean mIsRecordingVideo;

    /**
     * <p>开启一个后台线程,防止阻塞主UI</p>
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;

    /**
     * <p>后台线程的Handler</p>
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    /**
     * <p>相机锁</p>
     * <p>
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    /**
     * 设备传感器的方向
     */
    private Integer mSensorOrientation;
    /**
     * 录像存储位置
     */
    private String videoAbsolutePath;

    /**
     * 录像存储位置_临时
     */
    private String videoAbsolutePathTemp;
    /**
     * 预览的参数设置对象
     */
    private CaptureRequest.Builder mPreviewBuilder;
    /**
     * 相机管理
     */
    private CameraManager mCameraManager;
    /**
     * 相机特性
     */
    private CameraCharacteristics mCameraCharacteristics;
    /**
     * 预览请求
     */
    private CaptureRequest mPreviewRequest;

    /**
     * 上下文环境
     */
    private Activity mActivity;


    private ExecutorService mExecutorService;

    /**
     * focus的图
     */
    private AnimationImageView mFocusImage;
    /**
     * Focus的Scale动画
     */
    private ScaleAnimation mScaleFocusAnimation;
    /**
     * UI线程的handler
     */
    private Handler mMainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case FOCUS_DISAPPEAR:
                    if (msg.obj == null) {
                        mFocusImage.stopFocus();
                        break;
                    }
                    Integer valueTimes = (Integer) msg.obj;
                    if (mFocusImage.mTimes == valueTimes) {
                        mFocusImage.stopFocus();
                    }
                    break;
            }
        }
    };
    /**
     * 摄像机角度
     */
    private int mOrientation;
    /**
     * 相机ID
     */
    private String cameraId;

    /**
     * @param activity            当前调用的Activity
     * @param mAutoFitTextureView 相机控件
     * @param videoAbsolutePath   视频保存路径
     */
    public Camera2Helper(Activity activity, AutoFitTextureView mAutoFitTextureView, String videoAbsolutePath) {
        this.mActivity = activity;
        this.mAutoFitTextureView = mAutoFitTextureView;
        this.videoAbsolutePath = videoAbsolutePath;
        this.videoAbsolutePathTemp = videoAbsolutePath.replace(".mp4", "Temp.mp4");
        mExecutorService = Executors.newFixedThreadPool(3);
        // 初始化动画[对焦]
        mScaleFocusAnimation = new ScaleAnimation(2.0f, 1.0f, 2.0f, 1.0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        mScaleFocusAnimation.setDuration(200);
        // 初始化对焦组件 step1
        mFocusImage = activity.findViewById(R.id.videoRecord_focus);
        mFocusImage.setVisibility(View.INVISIBLE);
        mFocusImage.setmMainHandler(mMainHandler);
        mFocusImage.setmAnimation(mScaleFocusAnimation);
        // 初始化控件 step2
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, mActivity.getResources().getDisplayMetrics().widthPixels * 0.03f, activity.getResources().getDisplayMetrics()),
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, mActivity.getResources().getDisplayMetrics().widthPixels * 0.03f, activity.getResources().getDisplayMetrics()));
        layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        mFocusImage.setLayoutParams(layoutParams);
        mFocusImage.initFocus();

        // 初始化参数
        initParameter();
    }


    /**
     * 真实的预览帧,宽>高
     */
    public Size getPreviewSize() {
        return mPreviewSize;
    }

    /**
     * 开闪光灯
     */
    public void openFlash() {
        mPreviewBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
        try {
            mCameraCaptureSession.stopRepeating();
            mCameraCaptureSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 关闪光灯
     */
    public void closeFlash() {
        mPreviewBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
        try {
            mCameraCaptureSession.stopRepeating();
            mCameraCaptureSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    /**
     * 是否被授权
     *
     * @param permissions 权限列表
     */
    private boolean hasPermissionsGranted(String[] permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(mActivity, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * 初始化参数,相机锁加锁
     */
    private void initParameter() {
        if (!hasPermissionsGranted(VIDEO_PERMISSIONS)) {
            LogUtil.getInstance().d(TAG, "无权限");
            return;
        }
        final Activity activity = mActivity;
        if (null == activity || activity.isFinishing()) {
            return;
        }
        mCameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            LogUtil.getInstance().d(TAG, "tryAcquire");
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            cameraId = mCameraManager.getCameraIdList()[0];


            // Choose the sizes for camera preview and video recording
            mCameraCharacteristics = mCameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = mCameraCharacteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            mSensorOrientation = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            if (map == null) {
                throw new RuntimeException("Cannot get available preview/video sizes");
            }


            // 从配置文件加载分辨率设置
            mVideoSize = new Size(Integer.valueOf(Config.getInstance().getProperties().getProperty("VideoWidth")), Integer.valueOf(Config.getInstance().getProperties().getProperty("VideoHeight")));
            // 预览分辨率和录制分辨率保持一致
            mPreviewSize = mVideoSize;
//            mVideoSize = chooseVideoSize(map.getOutputSizes(SurfaceTexture.class));
//            mPreviewSize = chooseOptimalSize(map.getOutputSizes(MediaRecorder.class),screenWidth,screenHeight,mVideoSize);
            mOrientation = mActivity.getResources().getConfiguration().orientation;
            if (mOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                mAutoFitTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            } else {
                mAutoFitTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
            }
            mPreviewSessionCallback = new PreviewSessionCallback(mFocusImage, mMainHandler, mAutoFitTextureView);
        } catch (CameraAccessException e) {
            LogUtil.getInstance().e(activity, "Cannot access the camera.");
            activity.finish();
        } catch (NullPointerException e) {
            new ErrorDialog(mActivity, mActivity.getString(R.string.camera_error)).onCreateDialog().show();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.");
        }

    }

    /**
     * Tries to open a {@link CameraDevice}. The result is listened by `mStateCallback`.
     */
    @SuppressWarnings("MissingPermission")
    private void openCamera(int width, int height) {

        final Activity activity = mActivity;
        if (null == activity || activity.isFinishing()) {
            return;
        }
        try {
            configureTransform(width, height);
            mCameraManager.openCamera(cameraId, mStateCallback, null);
        } catch (CameraAccessException e) {
            LogUtil.getInstance().e(activity, "Cannot access the camera.");
            activity.finish();
        } catch (NullPointerException e) {
            new ErrorDialog(mActivity, mActivity.getString(R.string.camera_error)).onCreateDialog().show();
        }
    }

    /**
     * 关闭相机设备
     */
    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            closePreviewSession();
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mMediaRecorder) {
                if (!mIsRecordingVideo) {
                    File file = new File(videoAbsolutePathTemp);
                    if (file.exists()) {
                        if (!file.delete()) {
                            file.deleteOnExit();
                            LogUtil.getInstance().d(TAG, "退出后删除临时文件");
                        }
                    } else {
                        LogUtil.getInstance().d(TAG, "删除临时文件成功");
                    }
                }
                mExecutorService.submit(new Runnable() {
                    @Override
                    public void run() {
                        mMediaRecorder.release();
                        mMediaRecorder = null;
                    }
                });
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.");
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * 开始预览,同时输出到MediaRecorder
     */
    private void readyToPreview() {
        if (null == mCameraDevice || !mAutoFitTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        try {
            closePreviewSession();
            setupMediaRecorder();
            SurfaceTexture surfaceTexture = mAutoFitTextureView.getSurfaceTexture();
            assert surfaceTexture != null;
            surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            mPreviewBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CameraMetadata.CONTROL_CAPTURE_INTENT_VIDEO_SNAPSHOT);
            // 初始化参数
            mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_OFF);
            // 边缘增强,高质量
            mPreviewBuilder.set(CaptureRequest.EDGE_MODE, CameraMetadata.EDGE_MODE_HIGH_QUALITY);
            // 3A--->auto
            mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            // 3A
            mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO);
            mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
            mPreviewBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO);


            List<Surface> surfaces = new ArrayList<>();
            // Set up Surface for the camera preview
            Surface previewSurface = new Surface(surfaceTexture);
            surfaces.add(previewSurface);
            mPreviewBuilder.addTarget(previewSurface);
            // Set up Surface for the MediaRecorder
            Surface recorderSurface = mMediaRecorder.getSurface();
            surfaces.add(recorderSurface);
            mPreviewBuilder.addTarget(recorderSurface);

            // Start a capture session
            // Once the session starts, we can update the UI and start recording
            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Camera2Helper.this.mCameraCaptureSession = cameraCaptureSession;
                    startPreview();
                    mAutoFitTextureView.setMyTextureViewTouchEvent(new TextureViewTouchEvent(mCameraCharacteristics, mAutoFitTextureView,
                            mPreviewBuilder, Camera2Helper.this.mCameraCaptureSession, mPreviewRequest, mBackgroundHandler, mPreviewSessionCallback, mPreviewSize, mSensorOrientation));
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Activity activity = mActivity;
                    if (null != activity) {
                        LogUtil.getInstance().e(activity, "Failed");
                    }
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException | IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * <p>开始预览</p>
     */
    private void startPreview() {
        if (null == mCameraDevice) {
            return;
        }
        try {
            setUpCaptureRequestBuilder(mPreviewBuilder);
            mPreviewRequest = mPreviewBuilder.build();
            mCameraCaptureSession.setRepeatingRequest(mPreviewRequest, mPreviewSessionCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 设置预览参数
     */
    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
//        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
//            builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO);
    }

    /**
     * Configures the necessary {@link Matrix} transformation to `mAutoFitTextureView`.
     * This method should not to be called until the camera preview size is determined in
     * openCamera, or until the size of `mAutoFitTextureView` is fixed.
     *
     * @param viewWidth  The width of `mAutoFitTextureView`
     * @param viewHeight The height of `mAutoFitTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = mActivity;
        if (null == mAutoFitTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        mAutoFitTextureView.setTransform(matrix);
    }

    private void setupMediaRecorder() throws IOException {
        final Activity activity = mActivity;
        if (null == activity) {
            return;
        }

        // 无声模式
//        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setOutputFile(videoAbsolutePathTemp);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mMediaRecorder.setVideoEncodingBitRate(10 * mVideoSize.getWidth() * mVideoSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        // I帧间隔
        mMediaRecorder.setVideoFrameRate(20);
//        mMediaRecorder.setMaxDuration(1000 * 10);
        mMediaRecorder.setOnErrorListener(new MediaRecorder.OnErrorListener() {
            @Override
            public void onError(MediaRecorder mr, int what, int extra) {
                LogUtil.getInstance().e(TAG, "what:" + what + ", extra:" + extra);
            }
        });
        mMediaRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
            @Override
            public void onInfo(MediaRecorder mr, int what, int extra) {
                LogUtil.getInstance().d(TAG, "what:" + what + ", extra:" + extra);
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    activity.findViewById(R.id.videoRecord_btn_start).performClick();
                }
            }
        });
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        switch (mSensorOrientation) {
            case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                mMediaRecorder.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation));
                break;
            case SENSOR_ORIENTATION_INVERSE_DEGREES:
                mMediaRecorder.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation));
                break;
        }
        mMediaRecorder.prepare();
    }

    public void startRecordingVideo() {
        mIsRecordingVideo = true;
        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                // Start recording
                mMediaRecorder.start();
            }
        });
    }

    /**
     * 关闭预览的Session
     */
    private void closePreviewSession() {
        if (mCameraCaptureSession != null) {
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }
    }

    public void stopRecordingVideoAndPreview() {
        // 状态更新
        mIsRecordingVideo = false;

        // Stop recording
        mMediaRecorder.stop();
        mMediaRecorder.reset();

        Activity activity = mActivity;
        File file = new File(videoAbsolutePathTemp);
        try {
            FileOutputStream fos = new FileOutputStream(file, true);
            FileLock fileLock = fos.getChannel().lock();
            fileLock.release();
            fos.close();
            if (file.renameTo(new File(videoAbsolutePath))) {
                if (null != activity) {
                    LogUtil.getInstance().d(activity, "Video saved: " + videoAbsolutePath);
                    LogUtil.getInstance().d(TAG, "Video saved: " + videoAbsolutePath);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        readyToPreview();
    }

    public void stopRecordingVideo() {
        // 状态更新
        mIsRecordingVideo = false;

        // Stop recording
        mMediaRecorder.stop();
        mMediaRecorder.reset();

        Activity activity = mActivity;
        File file = new File(videoAbsolutePathTemp);
        try {
            FileOutputStream fos = new FileOutputStream(file, true);
            FileLock fileLock = fos.getChannel().lock();
            fileLock.release();
            fos.close();
            if (file.renameTo(new File(videoAbsolutePath))) {
                if (null != activity) {
                    LogUtil.getInstance().d(activity, "Video saved: " + videoAbsolutePath);
                    LogUtil.getInstance().d(TAG, "Video saved: " + videoAbsolutePath);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isRecordingVideo() {
        return mIsRecordingVideo;
    }

    /**
     * 选择Video分辨率
     *
     * @param choices The list of available sizes
     * @return The video size
     */
    private static Size chooseVideoSize(Size[] choices) {
        List<Size> bigEnough = new ArrayList<>();
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * 4 / 3) {
                bigEnough.add(size);
//            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
//                return size;
            }
        }
        if (bigEnough.size() > 0) {
            bigEnough.remove(Collections.max(bigEnough, new CompareSizesByArea()));
            return Collections.max(bigEnough, new CompareSizesByArea());
        } else {
            LogUtil.getInstance().e(TAG, "Couldn't find any suitable video size");
            return choices[0];
        }
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the respective requested values, and whose aspect
     * ratio matches with the specified value.
     *
     * @param choices     The list of sizes that the camera supports for the intended output class
     * @param width       The minimum desired width
     * @param height      The minimum desired height
     * @param aspectRatio The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() == option.getHeight() * w * 1f / h &&
                    option.getHeight() >= width && option.getWidth() >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
//            return Collections.min(bigEnough, new CompareSizesByArea());
            // 预览最大像素,更清晰
            return Collections.max(bigEnough, new CompareSizesByArea());
        } else {
            LogUtil.getInstance().e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    /**
     * 开启一个后台线程,不会阻塞UI<p>
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * 停止后台线程<p>
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        if (mBackgroundThread != null) {
            mBackgroundThread.quitSafely();
            try {
                mBackgroundThread.join();
                mBackgroundThread = null;
                mBackgroundHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 用于Activity的onResume状态
     */
    public void onResume() {
        startBackgroundThread();
        if (mAutoFitTextureView.isAvailable()) {
            openCamera(mAutoFitTextureView.getWidth(), mAutoFitTextureView.getHeight());
        } else {
            mAutoFitTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    /**
     * 用于Activity的onPause状态
     */
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
    }

    /**
     * 用于Activity的onDestroy状态
     */
    public void onDestroy() {
        closeCamera();
        stopBackgroundThread();
    }


    private static class ErrorDialog {
        private String message;
        private Activity activity;

        private ErrorDialog(Activity activity, String message) {
            this.activity = activity;
            this.message = message;
        }

        private Dialog onCreateDialog() {
            return new AlertDialog.Builder(activity)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    })
                    .create();
        }
    }

    private static class ConfirmationDialog {
        private static Dialog createDialog(final Activity activity) {
            return new AlertDialog.Builder(activity)
                    .setMessage(R.string.permission_request)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ActivityCompat.requestPermissions(activity, VIDEO_PERMISSIONS, REQUEST_VIDEO_PERMISSIONS);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    activity.finish();
                                }
                            })
                    .create();
        }
    }
}
