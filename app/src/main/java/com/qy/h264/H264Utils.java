package com.qy.h264;

/**
 * Created by Administrator on 2015/4/28.
 */

public class H264Utils {

    public static byte[] makePackage(byte[] byInput) {
        byte[] byOutput ;
        if (byInput[0] != 0x17 && byInput[0] != 0x27 ) {
            //Log.d("H264Utils", "makePackage error");
        }
        int nOffset = 0;
        nOffset += 5; // 0x27 1 0 0 0
        int length = ((byInput[nOffset] & 0x000000ff) << 24) | ((byInput[nOffset + 1] & 0x000000ff) << 16) | ((byInput[nOffset + 2] & 0x000000ff) << 8) | (byInput[nOffset + 3] & 0x000000ff);
        nOffset += 4;
        if (length > 1024 * 1024 * 10) {
          //  Log.d("H264Utils", "makePackage length error. length = " + length);
            return null;
        }
        try {
            byOutput = new byte[length + 4];
        } catch (Exception e) {
            return null;
        }

        byOutput[0] = 0;
        byOutput[1] = 0;
        byOutput[2] = 0;
        byOutput[3] = 1;

        System.arraycopy(byInput, nOffset, byOutput, 4, length);
        return byOutput;
    }

    public static byte[] makeConfig(byte[] byInput) {
        int cursor = 11;
        // sps length
        int spsLength = ((byInput[cursor] & 0x000000ff) << 8) | (byInput[cursor + 1] & 0x000000ff);
        cursor += 2;

        byte[] sps = new byte[spsLength];
        for (int i = 0; i < spsLength; i++) {
            sps[i] = byInput[cursor++];
        }

        cursor++;
        // pps length
        int ppsLength = ((byInput[cursor] & 0x000000ff) << 8) | (byInput[cursor + 1] & 0x000000ff);
        cursor += 2;

        byte[] pps = new byte[ppsLength];
        for (int i = 0; i < ppsLength; i++) {
            pps[i] = byInput[cursor++];
        }

        byte[] outConfig = new byte[8 + ppsLength + spsLength];
        outConfig[0] = 0;
        outConfig[1] = 0;
        outConfig[2] = 0;
        outConfig[3] = 1;

        System.arraycopy(sps, 0, outConfig, 4, spsLength);

        outConfig[4 + spsLength] = 0;
        outConfig[4 + spsLength + 1] = 0;
        outConfig[4 + spsLength + 2] = 0;
        outConfig[4 + spsLength + 3] = 1;
        for (int i = 0; i < ppsLength; i++) {
            outConfig[8 + spsLength + i] = pps[i];
        }

        return outConfig;
    }

    public static class H264Info {
        public H264Info() {}
        public int width;
        public int height;

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public int getHeight() {
            return height;
        }

        public void setHeight(int height) {
            this.height = height;
        }

        @Override
        public String toString() {
            return "H264Info{" +
                    "width=" + width +
                    ", height=" + height +
                    '}';
        }
    }

    public static H264Info h264_decode_seq_parameter_set(byte[] buf)
    {
        mStartBit = 0;
        int forbidden_zero_bit=u(1,buf);
        int nal_ref_idc=u(2,buf);
        int nal_unit_type=u(5,buf);
        if(nal_unit_type==7)
        {
            int profile_idc=u(8,buf);
            int constraint_set0_flag=u(1,buf);//(buf[1] & 0x80)>>7;
            int constraint_set1_flag=u(1,buf);//(buf[1] & 0x40)>>6;
            int constraint_set2_flag=u(1,buf);//(buf[1] & 0x20)>>5;
            int constraint_set3_flag=u(1,buf);//(buf[1] & 0x10)>>4;
            int reserved_zero_4bits=u(4,buf);
            int level_idc=u(8,buf);

            long seq_parameter_set_id=Ue(buf,buf.length);

            if( profile_idc == 100 || profile_idc == 110 ||
                    profile_idc == 122 || profile_idc == 144 )
            {
                long chroma_format_idc=Ue(buf,buf.length);
                if( chroma_format_idc == 3 ) {
                    long residual_colour_transform_flag = u(1, buf);
                }
                long bit_depth_luma_minus8=Ue(buf,buf.length);
                long bit_depth_chroma_minus8=Ue(buf,buf.length);
                long qpprime_y_zero_transform_bypass_flag=u(1,buf);
                long seq_scaling_matrix_present_flag=u(1, buf);

                int[] seq_scaling_list_present_flag = new int[8];
                if( seq_scaling_matrix_present_flag != 0 )
                {
                    for( int i = 0; i < 8; i++ ) {
                        seq_scaling_list_present_flag[i]=u(1,buf);
                    }
                }
            }
            long log2_max_frame_num_minus4=Ue(buf,buf.length);
            long pic_order_cnt_type=Ue(buf,buf.length);
            if( pic_order_cnt_type == 0 ) {
                long log2_max_pic_order_cnt_lsb_minus4 = Ue(buf, buf.length);
            } else if( pic_order_cnt_type == 1 ) {
                int delta_pic_order_always_zero_flag=u(1,buf);

                int offset_for_non_ref_pic=Se(buf, buf.length);
                int offset_for_top_to_bottom_field=Se(buf, buf.length);
                long num_ref_frames_in_pic_order_cnt_cycle=Ue(buf, buf.length);

                int[] offset_for_ref_frame = new int[(int)num_ref_frames_in_pic_order_cnt_cycle];
                for( int i = 0; i < num_ref_frames_in_pic_order_cnt_cycle; i++ ) {
                    offset_for_ref_frame[i] = Se(buf, buf.length);
                }
            }

            long num_ref_frames=Ue(buf, buf.length);
            int gaps_in_frame_num_value_allowed_flag=u(1, buf);

            long pic_width_in_mbs_minus1=Ue(buf, buf.length);
            long pic_height_in_map_units_minus1=Ue(buf, buf.length);

            H264Info info = new H264Info();
            info.width=(int)((pic_width_in_mbs_minus1+1)*16);
            info.height=(int)((pic_height_in_map_units_minus1+1)*16);

            return info;
        }

        return null;
    }

    public static String bytes2Hex(byte[] src, int offset, int length){
        String res = "";
        final char hexDigits[]={'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
        for(int i=offset,j=0; i<offset+length; i++){
            res += hexDigits[(src[i] >> 4) & 0x0f];
            res += hexDigits[src[i] & 0x0f];
            res += ' ';
        }
        return res;
    }

    public static long bytes2long(byte[] buffer, int offset) {
        long num = 0;
        for (int i = 0; i < 8; ++i) {
            int of = (7-i) * 8;
            num |= ((buffer[i+offset] & 0xff) << of);
        }
        return num;
    }

    public static int bytes2Int(byte[] buffer, int offset) {
        int num = 0;
        for (int i = 0; i < 4; ++i) {
            int of = i * 8;
            num |= ((buffer[i+offset] & 0xff) << of);
        }
        return num;
    }

    private static long Ue(byte[] buff, long nLen)
    {
        //计算0bit的个数
        long nZeroNum = 0;
        while (mStartBit < nLen * 8)
        {
            if ( (buff[mStartBit / 8] & (0x80 >> (mStartBit % 8))) != 0 ) //&:按位与，%取余
            {
                break;
            }
            nZeroNum++;
            mStartBit++;
        }
        mStartBit ++;

        //计算结果
        int ret = 0;
        for (int i=0; i<nZeroNum; i++)
        {
            ret <<= 1;
            if ((buff[mStartBit / 8] & (0x80 >> (mStartBit % 8))) != 0)
            {
                ret += 1;
            }
            mStartBit++;
        }
        return (1 << nZeroNum) - 1 + ret;
    }


    private static int Se(byte[] buff, int nLen)
    {
        long UeVal=Ue(buff, nLen);
        double k = UeVal;
        int nValue = (int) Math.ceil(k/2);//ceil函数：ceil函数的作用是求不小于给定实数的最小整数。ceil(2)=ceil(1.2)=cei(1.5)=2.00
        if (UeVal % 2==0)
            nValue=-nValue;
        return nValue;
    }

    private static int u(int BitCount, byte[] buf)
    {
        int dwRet = 0;
        for (int i=0; i<BitCount; i++)
        {
            dwRet <<= 1;
            if ((buf[mStartBit / 8] & (0x80 >> (mStartBit % 8))) != 0 )
            {
                dwRet += 1;
            }
            mStartBit++;
        }
        return dwRet;
    }

    private static int mStartBit;


    public static H264Utils.H264Info parseSpspps(byte[] byBuf) {
        byte[] tmp = new byte[byBuf.length - 4];
        System.arraycopy(byBuf, 4, tmp, 0, tmp.length);
        return H264Utils.h264_decode_seq_parameter_set(tmp);
    }
}
