#chatplatform
* 超时下线功能
* Message拦截器
* 监听任务重分配
* 完美实现负载均衡(交给主线程来做，任务线程排队在安全点等待即可)
* 更改负载均衡(交给任务线程的其中一个来做，主线程采用阻塞的ServerSocketChannel，进行dispatcher时，加上负载均衡锁)