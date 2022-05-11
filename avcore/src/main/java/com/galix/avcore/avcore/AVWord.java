package com.galix.avcore.avcore;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.widget.EditText;

import com.galix.avcore.render.IRender;
import com.galix.avcore.util.GLUtil;

/**
 * 文字特效组件
 */
public class AVWord extends AVComponent {

    public static final String CONFIG_USE_BITMAP = "use_bitmap_texture";
    private String text = "HelloWorld!";
    private EditText editText;
    private Canvas canvas;
    private Bitmap bitmap;
    private Paint paint;

    public AVWord(long srcStartTime, EditText editText, IRender render) {
        super(srcStartTime, AVComponentType.WORD, render);
        this.editText = editText;
    }

    @Override
    public int open() {
        setDuration(5000000);//TODO
        setEngineEndTime(getEngineStartTime() + getDuration());
        markOpen(true);
        canvas = new Canvas();
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        return RESULT_OK;
    }

    @Override
    public int close() {
        if (!isOpen()) return RESULT_OK;
        canvas = null;
        if (bitmap != null) {
            bitmap.recycle();
            bitmap = null;
        }
        paint = null;
        return RESULT_OK;
    }

    @Override
    public int readFrame() {
        if (needFreshBitmap()) {
            if (bitmap != null) {
                bitmap.recycle();
            }
            bitmap = Bitmap.createBitmap(editText.getWidth(), editText.getHeight(), Bitmap.Config.ARGB_8888);
            canvas.setBitmap(bitmap);
            paint.setTypeface(editText.getTypeface());
            paint.setTextSize(editText.getTextSize());
            paint.setColor(editText.getCurrentTextColor());
            canvas.drawText(editText.getText().toString(), 0, bitmap.getHeight() - editText.getTextSize(), paint);
            if (peekFrame().getTexture().id() != 0) {
                peekFrame().getTexture().release();
            }
            peekFrame().getTexture().idAsBuf().put(GLUtil.loadTexture(bitmap));
            peekFrame().getTexture().setOes(false);
            peekFrame().getTexture().setSize(bitmap.getWidth(), bitmap.getHeight());
            peekFrame().setBitmap(bitmap);
            text = editText.getText().toString();
        }
        peekFrame().setValid(true);
        peekFrame().setText(text);
        return RESULT_OK;
    }

    @Override
    public int seekFrame(long position) {
        setPosition(position);
        return readFrame();
    }

    private boolean needFreshBitmap() {
        boolean useBitmap = getConfigs().containsKey(CONFIG_USE_BITMAP) && (Boolean) getConfigs().get(CONFIG_USE_BITMAP);
        return useBitmap && !text.equals(editText.getText().toString());
    }

}
