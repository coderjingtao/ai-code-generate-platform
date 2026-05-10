package dev.jingtao.aicodebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import dev.jingtao.aicodebackend.constant.UserConstant;
import dev.jingtao.aicodebackend.exception.ErrorCode;
import dev.jingtao.aicodebackend.exception.ThrowUtils;
import dev.jingtao.aicodebackend.mapper.ChatHistoryMapper;
import dev.jingtao.aicodebackend.model.dto.chathistory.ChatHistoryQueryRequest;
import dev.jingtao.aicodebackend.model.entity.App;
import dev.jingtao.aicodebackend.model.entity.ChatHistory;
import dev.jingtao.aicodebackend.model.entity.Users;
import dev.jingtao.aicodebackend.model.enums.ChatHistoryMessageTypeEnum;
import dev.jingtao.aicodebackend.service.AppService;
import dev.jingtao.aicodebackend.service.ChatHistoryService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话历史 服务层实现。
 *
 * @author <a href="https://github.com/coderjingtao">Jingtao Liu</a>
 */
@Service
@Slf4j
public class ChatHistoryServiceImpl extends ServiceImpl<ChatHistoryMapper, ChatHistory>  implements ChatHistoryService{

    @Resource
    @Lazy
    private AppService appService;

    @Override
    public boolean addChatHistory(Long appId, String message, String messageType, Long userId) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "Invalid app id");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "Invalid message");
        ThrowUtils.throwIf(StrUtil.isBlank(messageType), ErrorCode.PARAMS_ERROR, "Invalid message type");
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR, "Invalid user id");

        ChatHistoryMessageTypeEnum messageTypeEnum = ChatHistoryMessageTypeEnum.getEnumByValue(messageType);
        ThrowUtils.throwIf(messageTypeEnum == null, ErrorCode.PARAMS_ERROR, "Invalid message type: "+ messageType);

        // 插入数据库
        ChatHistory chatHistory = ChatHistory.builder()
                .appId(appId)
                .message(message)
                .messageType(messageType)
                .userId(userId)
                .build();
        return this.save(chatHistory);
    }

    @Override
    public boolean deleteByAppId(Long appId) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "Invalid app id");
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("appId", appId);
        return this.remove(queryWrapper);
    }

    @Override
    public QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest) {
        QueryWrapper queryWrapper = QueryWrapper.create();
        if( chatHistoryQueryRequest == null) return queryWrapper;
        Long id = chatHistoryQueryRequest.getId();
        String message = chatHistoryQueryRequest.getMessage();
        String messageType = chatHistoryQueryRequest.getMessageType();
        Long appId = chatHistoryQueryRequest.getAppId();
        Long userId = chatHistoryQueryRequest.getUserId();
        LocalDateTime lastCreateTime = chatHistoryQueryRequest.getLastCreateTime();
        String sortField = chatHistoryQueryRequest.getSortField();
        String sortOrder = chatHistoryQueryRequest.getSortOrder();
        //拼接查询条件
        queryWrapper.eq("id", id)
                .like("message", message)
                .eq("messageType", messageType)
                .eq("appId", appId)
                .eq("userId", userId);
        //游标查询：只使用createTime 作为游标
        if(lastCreateTime != null) {
            queryWrapper.lt("createTime", lastCreateTime);
        }
        //排序
        if(StrUtil.isNotBlank(sortField)) {
            queryWrapper.orderBy(sortField, "ascend".equals(sortOrder));
        }else{
            //默认按照createTime 降序排列
            queryWrapper.orderBy("createTime", false);
        }
        return queryWrapper;
    }

    @Override
    public Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize, LocalDateTime lastCreateTime, Users loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "Invalid app id");
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.PARAMS_ERROR, "Invalid user id");
        ThrowUtils.throwIf(pageSize <= 0 || pageSize > 50, ErrorCode.PARAMS_ERROR, "Page size must be between 1 and 50");
        // 验证权限：只有应用的创建者，或者管理员才可以查看
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "App not found by id: "+ appId);
        boolean isCreator = app.getUserId().equals(loginUser.getId());
        boolean isAdmin = StrUtil.equals(UserConstant.ADMIN_ROLE,loginUser.getUserRole());
        ThrowUtils.throwIf(!isCreator && !isAdmin, ErrorCode.NO_AUTH_ERROR, "No permission to view this app's chat history");
        // 构建查询条件
        ChatHistoryQueryRequest request = new ChatHistoryQueryRequest();
        request.setAppId(appId);
        request.setLastCreateTime(lastCreateTime);
        QueryWrapper queryWrapper = this.getQueryWrapper(request);
        // 查询具体数据
        return this.page(Page.of(1, pageSize), queryWrapper);
    }

    @Override
    public int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxLoadCount) {
        try{
            QueryWrapper queryWrapper = QueryWrapper.create()
                    .eq(ChatHistory::getAppId, appId)
                    .orderBy(ChatHistory::getCreateTime, false)
                    .limit(1, maxLoadCount);
            List<ChatHistory> historyList = this.list(queryWrapper);
            if(CollUtil.isEmpty(historyList)) return 0;
            // 反转聊天历史的时间，确保按照时间正序加载到AI记忆中（老的记录在前，新的记录在后）
            historyList = historyList.reversed();
            int loadCount = 0;
            // 先清理历史缓存，防止重复加载
            chatMemory.clear();
            // 按时间顺序和消息类型添加到记忆中
            for (ChatHistory history : historyList) {
                if(history.getMessageType().equals(ChatHistoryMessageTypeEnum.USERS.getValue())) {
                    chatMemory.add(UserMessage.from(history.getMessage()));
                }else if(history.getMessageType().equals(ChatHistoryMessageTypeEnum.AI.getValue())){
                    chatMemory.add(AiMessage.from(history.getMessage()));
                }
                loadCount++;
            }
            log.info("为 App Id: {} 成功加载了 {} 条历史消息", appId, loadCount);
            return loadCount;
        }catch (Exception e) {
            log.error("加载App Id: {} 历史消息失败，error : {}", appId, e.getMessage(), e);
            return 0;
        }
    }
}
