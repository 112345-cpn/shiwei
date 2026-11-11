import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class Test2 {
//    public void main(String[] args) {
//
//     int[] nums=new int[1,2,3,1];
//
//        System.out.println( res);
//    }

    public int rob(int[] nums) {
        int[] dp=new int[nums.length];
        if(nums.length==0) return nums [0];
        dp[0]=Math.max(nums[0],nums[1]);
        for(int i=2;i<nums.length;i++){
            dp[i]=Math.max(dp[i-1],dp[i-2]+nums[i]);
        }
        return dp[nums.length-1];
    }

    }



