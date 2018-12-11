public class TextureViewTouchEvent implements AutoFitTextureView.MyTextureViewTouchEvent {
    private CameraCharacteristics mCameraCharacteristics;
    private TextureView mTextureView;
    private CaptureRequest.Builder mPreviewBuilder;
    private CameraCaptureSession mPreviewSession;
    private CaptureRequest mPreviewRequest;
    private Handler mBackgroundHandler;
    private PreviewSessionCallback mPreviewSessionCallback;
    private Size mPreviewSize;
    private Integer mSensorOrientation;

    public TextureViewTouchEvent(CameraCharacteristics mCameraCharacteristics, TextureView mTextureView, CaptureRequest.Builder mPreviewBuilder,
                                 CameraCaptureSession mPreviewSession, CaptureRequest mPreviewRequest, Handler mBackgroundHandler,
                                 PreviewSessionCallback mPreviewSessionCallback, Size mPreviewSize, Integer mSensorOrientation) {
        this.mCameraCharacteristics = mCameraCharacteristics;
        this.mTextureView = mTextureView;
        this.mPreviewBuilder = mPreviewBuilder;
        this.mPreviewSession = mPreviewSession;
        this.mPreviewRequest = mPreviewRequest;
        this.mBackgroundHandler = mBackgroundHandler;
        this.mPreviewSessionCallback = mPreviewSessionCallback;
        this.mPreviewSize = mPreviewSize;
        this.mSensorOrientation = mSensorOrientation;
    }

    @Override
    public boolean onAreaTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // 先取相对于view上面的坐标
                double x = event.getX(), y = event.getY(), tmp;

                LogUtil.getInstance().d("shb", "原始: x--->" + x + ",,,y--->" + y);
                // 取出来的图像如果有旋转角度的话，则需要将宽高交换下
                int realPreviewWidth;
                int realPreviewHeight;
                if (Camera2Helper.SENSOR_ORIENTATION_DEFAULT_DEGREES == mSensorOrientation || Camera2Helper.SENSOR_ORIENTATION_INVERSE_DEGREES == mSensorOrientation) {
                    realPreviewWidth = mPreviewSize.getHeight();
                    realPreviewHeight = mPreviewSize.getWidth();
                } else {
                    realPreviewWidth = mPreviewSize.getWidth();
                    realPreviewHeight = mPreviewSize.getHeight();
                }

                // 计算摄像头取出的图像相对于view放大了多少，以及有多少偏移
                double imgScale = 1.0, verticalOffset = 0, horizontalOffset = 0;
                // mTextureView预览View的控件
                if (realPreviewHeight * mTextureView.getWidth() > realPreviewWidth * mTextureView.getHeight()) {
                    imgScale = mTextureView.getWidth() * 1.0 / realPreviewWidth;
                    verticalOffset = (realPreviewHeight - mTextureView.getHeight() / imgScale) / 2;
                } else {
                    imgScale = mTextureView.getHeight() * 1.0 / realPreviewHeight;
                    horizontalOffset = (realPreviewWidth - mTextureView.getWidth() / imgScale) / 2;
                }

                // 将点击的坐标转换为图像上的坐标
                x = x / imgScale + horizontalOffset;
                y = y / imgScale + verticalOffset;
                if (Camera2Helper.SENSOR_ORIENTATION_DEFAULT_DEGREES == mSensorOrientation) {
                    tmp = x;
                    x = y;
                    y = mPreviewSize.getHeight() - tmp;
                } else if (Camera2Helper.SENSOR_ORIENTATION_INVERSE_DEGREES == mSensorOrientation) {
                    tmp = x;
                    x = mPreviewSize.getWidth() - y;
                    y = tmp;
                }

                // 计算取到的图像相对于裁剪区域的缩放系数，以及位移
                Rect cropRegion = mPreviewRequest.get(CaptureRequest.SCALER_CROP_REGION);
                if (null == cropRegion) {
                    LogUtil.getInstance().e("TextureViewTouchEvent", "can't get crop region");
                    Size s = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
                    if (s != null) {
                        cropRegion = new Rect(0, 0, s.getWidth(), s.getHeight());
                    }
                }
                int cropWidth = cropRegion.width(), cropHeight = cropRegion.height();
                if (mPreviewSize.getHeight() * cropWidth > mPreviewSize.getWidth() * cropHeight) {
                    imgScale = cropHeight * 1.0 / mPreviewSize.getHeight();
                    verticalOffset = 0;
                    horizontalOffset = (cropWidth - imgScale * mPreviewSize.getWidth()) / 2;
                } else {
                    imgScale = cropWidth * 1.0 / mPreviewSize.getWidth();
                    horizontalOffset = 0;
                    verticalOffset = (cropHeight - imgScale * mPreviewSize.getHeight()) / 2;
                }

                // 将点击区域相对于图像的坐标，转化为相对于成像区域的坐标
                x = x * imgScale + horizontalOffset + cropRegion.left;
                y = y * imgScale + verticalOffset + cropRegion.top;
                double tapAreaRatio = 0.03;
                Rect rect = new Rect();
                rect.left = MathUtils.clamp((int) (x - tapAreaRatio / 2 * cropRegion.width()), 0, cropRegion.width() - 1);
                rect.right = MathUtils.clamp((int) (x + tapAreaRatio / 2 * cropRegion.width()), 0, cropRegion.width() - 1);
                rect.top = MathUtils.clamp((int) (y - tapAreaRatio / 2 * cropRegion.width()), 0, cropRegion.height() - 1);
                rect.bottom = MathUtils.clamp((int) (y + tapAreaRatio / 2 * cropRegion.width()), 0, cropRegion.height() - 1);

                mPreviewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
                mPreviewBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL);
                mPreviewBuilder.set(CaptureRequest.CONTROL_AE_LOCK, Boolean.FALSE);
                mPreviewBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{new MeteringRectangle(rect, 999)});
                mPreviewBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, new MeteringRectangle[]{new MeteringRectangle(rect, 999)});
                mPreviewBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
                mPreviewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
                try {
                    mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), mPreviewSessionCallback, mBackgroundHandler);
                } catch (CameraAccessException e) {
                    LogUtil.getInstance().e("TextureViewTouchEvent", "setRepeatingRequest failed, " + e.getMessage());
                }
                break;
            case MotionEvent.ACTION_UP:
                break;
        }
        return true;
    }

}
