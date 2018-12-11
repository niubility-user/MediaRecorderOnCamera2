public class PreviewSessionCallback extends CameraCaptureSession.CaptureCallback implements AutoFitTextureView.FocusPositionTouchEvent {

    private final static String TAG = "PreviewSessionCallback";
    private int mAfState = CameraMetadata.CONTROL_AF_STATE_INACTIVE;
    private int mRawX;
    private int mRawY;
    private boolean mFlagShowFocusImage = false;

    private AnimationImageView mFocusImage;
    /**
     * UI线程的Handler,更新UI用
     */
    private Handler mMainHandler;

    public PreviewSessionCallback(AnimationImageView mFocusImage, Handler mMainHandler, AutoFitTextureView mAutoFitTextureView) {
        this.mFocusImage = mFocusImage;
        this.mMainHandler = mMainHandler;
        mAutoFitTextureView.setFocusPositionTouchEvent(this);
    }

    @Override
    public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull final TotalCaptureResult result) {
        Integer nowAfState = result.get(CaptureResult.CONTROL_AF_STATE);
//        LogUtil.getInstance().d("status", "nowAfState:" + nowAfState + "mAfState:" + mAfState);
        //获取失败
        if (nowAfState == null) {
            return;
        }
        //这次的值与之前的一样，忽略掉
        if (nowAfState == mAfState) {
            return;
        }
        mAfState = nowAfState;
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                judgeFocus();
            }
        });
    }

    private void judgeFocus() {
        switch (mAfState) {
            case CameraMetadata.CONTROL_AF_STATE_ACTIVE_SCAN:
                LogUtil.getInstance().d("status", "CONTROL_AF_STATE_ACTIVE_SCAN");
            case CameraMetadata.CONTROL_AF_STATE_PASSIVE_SCAN:
                LogUtil.getInstance().d("status", "CONTROL_AF_STATE_PASSIVE_SCAN");
                focusFocusing();
                break;
            case CameraMetadata.CONTROL_AF_STATE_FOCUSED_LOCKED:
                LogUtil.getInstance().d("status", "CONTROL_AF_STATE_FOCUSED_LOCKED");
            case CameraMetadata.CONTROL_AF_STATE_PASSIVE_FOCUSED:
                LogUtil.getInstance().d("status", "CONTROL_AF_STATE_PASSIVE_FOCUSED");
                focusSucceed();
                break;
            case CameraMetadata.CONTROL_AF_STATE_INACTIVE:
                LogUtil.getInstance().d("status", "CONTROL_AF_STATE_INACTIVE");
                focusInactive();
                break;
            case CameraMetadata.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED:
                LogUtil.getInstance().d("status", "CONTROL_AF_STATE_NOT_FOCUSED_LOCKED");
            case CameraMetadata.CONTROL_AF_STATE_PASSIVE_UNFOCUSED:
                LogUtil.getInstance().d("status", "CONTROL_AF_STATE_PASSIVE_UNFOCUSED");
                focusFailed();
                break;
        }
    }

    private void focusFocusing() {
        //得到宽高
        int width = mFocusImage.getWidth();
        int height = mFocusImage.getHeight();
        //居中
        ViewGroup.MarginLayoutParams margin = new ViewGroup.MarginLayoutParams(mFocusImage.getLayoutParams());
        margin.setMargins(mRawX - width / 2, mRawY - height / 2, margin.rightMargin, margin.bottomMargin);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(margin);
        mFocusImage.setLayoutParams(layoutParams);
        //显示
        if (!mFlagShowFocusImage) {
            mFocusImage.startFocusing();
            mFlagShowFocusImage = true;
        }
    }

    private void focusSucceed() {
        if (mFlagShowFocusImage) {
            mFocusImage.focusSuccess();
            mFlagShowFocusImage = false;
        }
    }

    private void focusInactive() {
        mFocusImage.stopFocus();
        mFlagShowFocusImage = false;
    }

    private void focusFailed() {
        if (mFlagShowFocusImage) {
            mFocusImage.focusFailed();
            mFlagShowFocusImage = false;
        }
    }

    @Override
    public void getPosition(MotionEvent event) {
        mRawX = (int) event.getRawX();
        mRawY = (int) event.getRawY();
    }
}


