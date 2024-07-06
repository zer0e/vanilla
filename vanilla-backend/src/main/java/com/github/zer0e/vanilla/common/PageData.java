package com.github.zer0e.vanilla.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageData<T> {

    private Integer page;
    private Integer size;

    private Number count;

    private List<T> data;
}
