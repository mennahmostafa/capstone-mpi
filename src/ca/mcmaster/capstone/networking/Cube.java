package ca.mcmaster.capstone.networking;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.opengles.GL10;

class Cube {

    private FloatBuffer mVertexBuffer;
    private FloatBuffer mColorBuffer;
    private ByteBuffer mIndexBuffer;

    private float vertices[] = {
            -.6f, -1.0f, -.1f,
            .6f, -1.0f, -.1f,
            .6f,  1.0f, -.1f,
            -.6f, 1.0f, -.1f,
            -.6f, -1.0f,  .1f,
            .6f, -1.0f,  .1f,
            .6f,  1.0f,  .1f,
            -.6f,  1.0f,  .1f
    };
    public float color[] = {
            1.0f,  1.0f,  1.0f,  1.0f,
            1.0f,  1.0f,  1.0f,  1.0f,
            1.0f,  1.0f,  1.0f,  1.0f,
            1.0f,  1.0f,  1.0f,  1.0f,
            1.0f,  1.0f,  1.0f,  1.0f,
            1.0f,  1.0f,  1.0f,  1.0f,
            1.0f,  1.0f,  1.0f,  1.0f,
            1.0f,  1.0f,  1.0f,  1.0f
    };

    private float black[] = {
            0.0f,  0.0f,  0.0f,  0.0f,
            0.0f,  0.0f,  0.0f,  0.0f,
            0.0f,  0.0f,  0.0f,  0.0f,
            0.0f,  0.0f,  0.0f,  0.0f,
            0.0f,  0.0f,  0.0f,  0.0f,
            0.0f,  0.0f,  0.0f,  0.0f,
            0.0f,  0.0f,  0.0f,  0.0f,
            0.0f,  0.0f,  0.0f,  0.0f
    };

    private byte indices[] = {
            0, 4, 5, 0, 5, 1,
            1, 5, 6, 1, 6, 2,
            2, 6, 7, 2, 7, 3,
            3, 7, 4, 3, 4, 0,
            4, 7, 6, 4, 6, 5,
            3, 0, 1, 3, 1, 2
    };

    public Cube() {
        ByteBuffer byteBuf = ByteBuffer.allocateDirect(vertices.length * 4);
        byteBuf.order(ByteOrder.nativeOrder());
        mVertexBuffer = byteBuf.asFloatBuffer();
        mVertexBuffer.put(vertices);
        mVertexBuffer.position(0);

        byteBuf = ByteBuffer.allocateDirect(color.length * 4);
        byteBuf.order(ByteOrder.nativeOrder());
        mColorBuffer = byteBuf.asFloatBuffer();
        mColorBuffer.put(color);
        mColorBuffer.position(0);

        mIndexBuffer = ByteBuffer.allocateDirect(indices.length);
        mIndexBuffer.put(indices);
        mIndexBuffer.position(0);
    }

    public void draw(GL10 gl) {
        gl.glFrontFace(GL10.GL_CW);

        gl.glVertexPointer(3, GL10.GL_FLOAT, 0, mVertexBuffer);
        gl.glColorPointer(4, GL10.GL_FLOAT, 0, mColorBuffer);

        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glEnableClientState(GL10.GL_COLOR_ARRAY);

        mColorBuffer.clear();

        mColorBuffer.put(color);
        mColorBuffer.position(0);
        gl.glColorPointer(4, GL10.GL_FLOAT, 0, mColorBuffer);
        gl.glDrawElements(GL10.GL_TRIANGLES, 36, GL10.GL_UNSIGNED_BYTE,
                mIndexBuffer);

        mColorBuffer.clear();

        mColorBuffer.put(black);
        mColorBuffer.position(0);
        gl.glColorPointer(4, GL10.GL_FLOAT, 0, mColorBuffer);
        gl.glDrawElements(GL10.GL_LINES, 36, GL10.GL_UNSIGNED_BYTE,
                mIndexBuffer);

        //mColorBuffer.clear();

        gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glDisableClientState(GL10.GL_COLOR_ARRAY);
    }
}
