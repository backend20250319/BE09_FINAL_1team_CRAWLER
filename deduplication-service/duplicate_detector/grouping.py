# group_by_union_find	제목 유사도 계산, Union-Find 기반 그룹핑

import pandas as pd
from collections import defaultdict
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity
from duplicate_detector.preprocessing_title import preprocess_titles

def compute_title_similarity(df, threshold):
    df['clean_title'] = df['title'].apply(preprocess_titles)

    vectorizer = TfidfVectorizer()
    tfidf_matrix = vectorizer.fit_transform(df['clean_title'])
    similarity_matrix = cosine_similarity(tfidf_matrix)

    similar_pairs = []
    for i in range(len(df)):
        for j in range(i + 1, len(df)):
            if similarity_matrix[i][j] >= threshold:
                similar_pairs.append((i, j, similarity_matrix[i][j]))

    return similar_pairs

def group_by_union_find(similar_pairs):
    parent = {}

    def find(x):
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    def union(x, y):
        root_x, root_y = find(x), find(y)
        if root_x != root_y:
            parent[root_y] = root_x

    for i, j, _ in similar_pairs:
        if i not in parent: parent[i] = i
        if j not in parent: parent[j] = j
        union(i, j)

    groups_dict = defaultdict(set)
    for node in parent:
        groups_dict[find(node)].add(node)

    return list(groups_dict.values())

def build_title_similarity_groups(df, threshold):
    similar_pairs = compute_title_similarity(df, threshold)
    groups = group_by_union_find(similar_pairs)
    return groups, similar_pairs    
