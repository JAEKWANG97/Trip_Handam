package com.ssafy.handam.chat.service;

import com.ssafy.handam.chat.client.UserApiClient;
import com.ssafy.handam.chat.client.UserDto;
import com.ssafy.handam.chat.controller.response.ChatResponse;
import com.ssafy.handam.chat.controller.response.ChatRoomsResponse;
import com.ssafy.handam.chat.domain.ChatMessage;
import com.ssafy.handam.chat.domain.ChatRoom;
import com.ssafy.handam.chat.dto.ChatMessageDto;
import com.ssafy.handam.chat.dto.ChatUserDto;
import com.ssafy.handam.chat.repository.ChatMessageRepository;
import com.ssafy.handam.chat.repository.ChatRoomRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@Transactional
@RequiredArgsConstructor
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserApiClient userApiClient;

    public List<ChatRoomsResponse> getChatRoomsByUserId(Long userId, String token) {
        List<ChatRoom> chatRooms = chatRoomRepository.findByUserIdsContaining(userId);

        return chatRooms.stream().map(chatRoom -> {
            List<Long> userIds = new ArrayList<>(chatRoom.getUserIds());
            userIds.remove(userId);
            Long partnerId = userIds.get(0);

            Optional<ChatMessage> latestMessageOpt = chatMessageRepository.findFirstByChatRoom_ChatRoomIdOrderByCreatedDateDesc(
                    chatRoom.getChatRoomId());

            String nickname;
            String content;
            LocalDateTime createdDate;
            UserDto user = userApiClient.getUserById(partnerId, token);
            ChatUserDto chatUserDto = ChatUserDto.from(user);

            if (latestMessageOpt.isPresent()) {
                ChatMessage latestMessage = latestMessageOpt.get();

                UserDto senderUser = userApiClient.getUserById(latestMessage.getSenderId(), token);
                nickname = senderUser.nickname();
                content = latestMessage.getContent();
                createdDate = latestMessage.getCreatedDate();
            } else {
                UserDto partnerUser = userApiClient.getUserById(partnerId, token);
                nickname = partnerUser.nickname();
                content = "";
                createdDate = null;
            }

            return ChatRoomsResponse.of(
                    chatRoom.getChatRoomId(),
                    nickname,
                    content,
                    createdDate,
                    chatUserDto
            );
        }).toList();
    }

    public void saveMessage(ChatMessage chatMessage) {
        chatMessageRepository.save(chatMessage);
    }


    public List<ChatResponse> getChatByRoomId(Long roomId, Pageable pageable) {
        List<ChatMessage> content = chatMessageRepository.findByChatRoom_ChatRoomId(roomId, pageable).getContent();
        return content.stream()
                .map(chatMessage -> ChatResponse.of(
                        chatMessage.getChatRoom().getChatRoomId(),
                        chatMessage.getSenderId(),
                        chatMessage.getContent(),
                        chatMessage.getCreatedDate()))
                .toList();
    }


    public ChatRoom getChatRoomsByRoomId(Long roomId) {
        return chatRoomRepository.findById(roomId).orElse(null);
    }

    public ChatRoom createChatRoom(Long userId, Long partnerId) {
        ChatRoom chatRoom = new ChatRoom(List.of(userId, partnerId));
        return chatRoomRepository.save(chatRoom);
    }

    public ChatMessageDto convertToDto(ChatMessage chatMessage) {
        return new ChatMessageDto(
                chatMessage.getMessageId(),
                chatMessage.getChatRoom().getChatRoomId(),
                chatMessage.getSenderId(),
                chatMessage.getContent(),
                chatMessage.getCreatedDate()
        );
    }
}
