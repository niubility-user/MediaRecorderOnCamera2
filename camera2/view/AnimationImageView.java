public class AnimationImageView extends android.support.v7.widget.AppCompatImageView {
    private Handler mMainHandler;
    private Animation mAnimation;
    private Context mContext;
    /**
     * 防止又换了个text，但是上次哪个还没有消失即将小时就把新的text的给消失了
     */
    public int mTimes = 0;

    public AnimationImageView(Context context) {
        super(context);
        mContext = context;
    }

    public AnimationImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    public AnimationImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context;
    }

    public void setmMainHandler(Handler mMainHandler) {
        this.mMainHandler = mMainHandler;
    }

    public void setmAnimation(Animation mAnimation) {
        this.mAnimation = mAnimation;
    }

    public void initFocus() {
        this.setVisibility(VISIBLE);
        new Thread(new SleepThread(mMainHandler, Camera2Helper.FOCUS_DISAPPEAR, 1, null)).start();
    }

    public void startFocusing() {
        mTimes++;
        this.setVisibility(View.VISIBLE);
        this.startAnimation(mAnimation);
        this.setBackground(mContext.getDrawable(R.mipmap.ic_focus_start));
        new Thread(new SleepThread(mMainHandler, Camera2Helper.FOCUS_DISAPPEAR, 1000, mTimes)).start();
    }

    public void focusFailed() {
        mTimes++;
        this.setBackground(mContext.getDrawable(R.mipmap.ic_focus_failed));
        new Thread(new SleepThread(mMainHandler, Camera2Helper.FOCUS_DISAPPEAR, 800, mTimes)).start();
    }

    public void focusSuccess() {
        mTimes++;
        this.setVisibility(View.VISIBLE);
        this.setBackground(mContext.getDrawable(R.mipmap.ic_focus_succeed));
        new Thread(new SleepThread(mMainHandler, Camera2Helper.FOCUS_DISAPPEAR, 800, mTimes)).start();
    }

    public void stopFocus() {
        this.setVisibility(INVISIBLE);
    }
}
