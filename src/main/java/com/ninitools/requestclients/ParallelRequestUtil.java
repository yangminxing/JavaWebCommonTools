package main.java.com.ninitools.requestclients;


import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;

/**
 * ���첽���󹤾���
 * 1.List�����˳��ͬ���캯���Ĳ���˳��
 * 2.���캯���Ĳ�����������ȫ��ͬ�������������Ļ��᲻��ȷ
 * 3.��������ʱ��Throws Exception
 * try
 * {
 *     List<String> [RESULT] =new ParallelRequestUtil([TimeOut], [URL1], [URL2],[URL3]....[URLN]).getResult(); //List���˳�� URL1���� URL2����...
 *     List<String> resultString_1=new ParallelRequestUtil(3000,"http://192.168.0.1/usr?delay=1","http://192.168.0.2").getResult(); //ָ����ʱʱ�� 3Sec
 *     List<String> resultString_2=new ParallelRequestUtil("http://192.168.0.1","http://192.168.0.2").getResult(); //Ĭ�ϳ�ʱʱ�� 5Sec
 * }
 * catch(Exception e)
 * {
 *
 * }
 *
 * @author ymx
 */
public class ParallelRequestUtil {

    //��ʱʱ��
    private int timeOut;
    //����URL���ϣ�Set��ֹ��ַ�ظ��������ַ�������ظ�
    private Map<String,Integer> requesturls=new HashMap<String,Integer>();

    public static void main(String[] args)
    {
        try
        {
            List<String> result =new ParallelRequestUtil(20000, "http://localhost:8081/usr2?delay=5000","http://localhost:8081/usr2?delay=200","http://localhost:8081/usr2?delay=1000").getResult();
            for(String rl:result)
            {
                System.out.println("result :"+rl);
            }
        }
        catch (Exception e)
        {
            System.out.println("err"+e);
        }
    }

    /**
     * �����๹�캯��
     *
     * @param timeOut ��ʱʱ�� (����)
     * @param urls �����ַ�����ذ���˳��
     */
    public ParallelRequestUtil(int timeOut, String... urls) throws Exception
    {
        if(urls==null||urls.length==0)
        {
            throw new Exception("ParallelRequestUtil ��������Ϊ��");
        }

        for(int sortNum=0;sortNum<urls.length;sortNum++)
        {
            if(requesturls.containsKey(urls[sortNum]))
                continue;
            requesturls.put(urls[sortNum], sortNum);
        }

        this.timeOut=timeOut;
    }

    /**
     * �����๹�캯��
     *
     * @param urls �����ַ�����ذ���˳��
     */
    public ParallelRequestUtil(String... urls) throws Exception
    {
        this(5000,urls);
    }

    public List<String> getResult() throws Exception
    {
        //����õĽ����
        List<String> linkedListResult=new LinkedList<String>();
        try
        {
            int urlSize=requesturls.size();
            //���ó�ʱʱ��
            RequestConfig requestConfig=RequestConfig.custom()
                    .setSocketTimeout(timeOut)
                    .setConnectionRequestTimeout(timeOut).build();

            //����Client����
            CloseableHttpAsyncClient httpclient= HttpAsyncClients.custom()
                    .setDefaultRequestConfig(requestConfig)
                    .build();

            try {
                //����Client����
                httpclient.start();

                //����URL
                final HttpGet[] requests = new HttpGet[urlSize];
                Iterator iter=requesturls.keySet().iterator();
                int stu=0;
                while(iter.hasNext())
                {
                    String p=iter.next().toString();
                    System.out.println(p);
                    requests[stu++] = new HttpGet(p);
                }

                //�����̼߳�����
                final CountDownLatch latch = new CountDownLatch(urlSize);

                //������� Map������Ľ��
                final Map<Integer,String> resultMap=new TreeMap<Integer, String>(new Comparator<Integer>() {
                    @Override
                    public int compare(Integer o1, Integer o2) {
                        return o1-o2;
                    }
                });

                //����������Ϣ
                final List<String> resultErrorList=new ArrayList<String>();

                for (final HttpGet request : requests) {
                    System.out.println("Start request"+request.toString());

                    //����������
                    final HttpContext context=HttpClientContext.create();
                    context.setAttribute("sourceurl", request.getURI().toString());

                    //��������
                    httpclient.execute(request, context, new FutureCallback<HttpResponse>() {
                        @Override
                        public void completed(HttpResponse httpResponse) {
                            try {
                                latch.countDown();
                                //��ӽ����
                                resultMap.put(((Integer)requesturls.get(context.getAttribute("sourceurl"))), EntityUtils.toString(httpResponse.getEntity()));

                            } catch (Exception e) {
                                this.failed(new IOException("ParallelRequestUtil ��ȡʵ������з�������!�������Ϊ : " + e));
                            }
                        }

                        @Override
                        public void failed(Exception e) {
                            latch.countDown();
                            resultErrorList.add("ParallelRequestUtil �������ʧ��!�����ַ:"+context.getAttribute("sourceurl")+".�������Ϊ : "+e);
                        }

                        @Override
                        public void cancelled() {
                            latch.countDown();
                            this.failed(new Exception("ParallelRequestUtil ����ȡ��!"));
                        }
                    });
                }
                latch.await();

                //���÷��ؽ����
                Iterator<Map.Entry<Integer,String>> it=resultMap.entrySet().iterator();
                while(it.hasNext())
                    linkedListResult.add(it.next().getValue());

                //��������
                if(resultErrorList.size()!=0)
                {
                    StringBuilder sb=new StringBuilder();
                    for(String err:resultErrorList)
                        sb.append(err);
                    throw new Exception(sb.toString());
                }

            }
            catch (Exception e)
            {
                throw e;
            }
            finally {
                //�ر�Client
                httpclient.close();
            }

        }
        catch (Exception e)
        {
            throw e;
        }
        //���
        return linkedListResult;
    }

}
