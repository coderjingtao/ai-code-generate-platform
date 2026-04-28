package dev.jingtao.aicodebackend.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import dev.jingtao.aicodebackend.model.dto.chathistory.ChatHistoryQueryRequest;
import dev.jingtao.aicodebackend.model.entity.ChatHistory;
import dev.jingtao.aicodebackend.model.entity.Users;

import java.time.LocalDateTime;

/**
 * 对话历史 服务层。
 *
 * @author <a href="https://github.com/coderjingtao">Jingtao Liu</a>
 */
public interface ChatHistoryService extends IService<ChatHistory> {
    /**
     * 添加对话历史
     *
     * @param appId       应用 id
     * @param message     消息
     * @param messageType 消息类型
     * @param userId      用户 id
     * @return 是否添加成功
     */
    boolean addChatHistory(Long appId, String message, String messageType, Long userId);

    /**
     * 根据应用 id 删除对话历史
     *
     * @param appId 应用 id
     * @return 是否删除成功
     */
    boolean deleteByAppId(Long appId);

    /**
     * 构造查询 chat history 的条件
     *
     * @param chatHistoryQueryRequest 查询请求
     * @return 查询条件
     */
    QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);

    /**
     * 分页查询某 APP 的对话记录
     *
     * @param appId 应用 id
     * @param pageSize 查询结果中每页的数量
     * @param lastCreateTime 最后一条记录的创建时间
     * @param loginUser 登录用户
     * @return 返回分页查询的结果
     */
    Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize,
                                               LocalDateTime lastCreateTime,
                                               Users loginUser);
}
