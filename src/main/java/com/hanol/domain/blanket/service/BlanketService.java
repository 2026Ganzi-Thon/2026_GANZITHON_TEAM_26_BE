package com.hanol.domain.blanket.service;

import com.hanol.domain.blanket.dto.response.BlanketSlotResponse;
import com.hanol.domain.blanket.dto.response.BlanketSlotSummaryResponse;
import com.hanol.domain.blanket.dto.response.BlanketViewResponse;
import com.hanol.domain.blanket.entity.Blanket;
import com.hanol.domain.blanket.entity.BlanketPiece;
import com.hanol.domain.blanket.repository.BlanketPieceRepository;
import com.hanol.domain.blanket.repository.BlanketRepository;
import com.hanol.domain.mood.repository.MoodRecordRepository;
import com.hanol.global.exception.BusinessException;
import com.hanol.global.exception.CommonErrorCode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 담요 번호(blanketId)는 DB id 가 아니라 이 사용자의 몇 번째 담요인지(1부터)다. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlanketService {

  private final BlanketRepository blanketRepository;
  private final BlanketPieceRepository blanketPieceRepository;
  private final MoodRecordRepository moodRecordRepository;

  public BlanketViewResponse getBlanket(Long userId, Long blanketNumber) {
    List<Long> blanketIds = blanketRepository.findIdsByUserIdOrderByIdAsc(userId);
    if (blanketIds.isEmpty()) {
      if (blanketNumber != null && blanketNumber != 1L) {
        throw new BusinessException(CommonErrorCode.NOT_FOUND);
      }
      return new BlanketViewResponse(1L, toSlots(Set.of()), false, false);
    }

    long number = blanketNumber == null ? blanketIds.size() : blanketNumber;
    Long dbId = resolveBlanketId(blanketIds, number);
    Set<Integer> filled = new HashSet<>(blanketPieceRepository.findSlotIndexesByBlanketId(dbId));
    return new BlanketViewResponse(number, toSlots(filled), number > 1, number < blanketIds.size());
  }

  public BlanketSlotResponse getSlot(Long userId, Long blanketNumber, int slotIndex) {
    if (slotIndex < 1 || slotIndex > Blanket.SLOT_COUNT) {
      throw new BusinessException(CommonErrorCode.BAD_REQUEST);
    }
    Long dbId =
        resolveBlanketId(blanketRepository.findIdsByUserIdOrderByIdAsc(userId), blanketNumber);
    BlanketPiece piece =
        blanketPieceRepository
            .findWithCheerByBlanketIdAndSlotIndex(dbId, slotIndex)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    boolean hasMoodRecord =
        moodRecordRepository.existsByUserIdAndRecordDate(
            userId, piece.getReceivedAt().toLocalDate());
    return BlanketSlotResponse.from(piece, hasMoodRecord);
  }

  private Long resolveBlanketId(List<Long> blanketIds, long blanketNumber) {
    if (blanketNumber < 1 || blanketNumber > blanketIds.size()) {
      throw new BusinessException(CommonErrorCode.NOT_FOUND);
    }
    return blanketIds.get((int) blanketNumber - 1);
  }

  private List<BlanketSlotSummaryResponse> toSlots(Set<Integer> filled) {
    return IntStream.rangeClosed(1, Blanket.SLOT_COUNT)
        .mapToObj(i -> new BlanketSlotSummaryResponse(i, filled.contains(i)))
        .toList();
  }
}
