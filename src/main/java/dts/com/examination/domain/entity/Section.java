package dts.com.examination.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Section implements Serializable {
    private String code;
    private String title;
    private Integer questionCount;
    private Integer score;
    private Integer order;
}
