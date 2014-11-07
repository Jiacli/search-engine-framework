import os
import re
import json

def parse_result(text, interest_set = None):
    begin_mark = 'uploaded ----'
    end_mark = '---- Done ----'
    if begin_mark not in text or end_mark not in text:
        return None
    text = text[text.find(begin_mark) + len(begin_mark) : text.find(end_mark)]
    qDict = dict()
    for line in text.split('\n'):
        seg = re.split(r'\s+', line)
        if len(seg) != 3:
            continue
        if not seg[1] in qDict:
            qDict[seg[1]] = dict()
        if interest_set and seg[0] not in interest_set:
            continue
        qDict[seg[1]][seg[0]] = seg[2]
    return qDict

def parse_time(text):
    for line in text.split('\n'):
        if 'Time used:' in line:
            seg = line.split(' ')
            return seg[2]
    return 'none'

def read_param(filename):
    param = dict()
    for line in open(filename, 'r'):
        seg = line.strip().split('=')
        if len(seg) != 2:
            continue
        param[seg[0]] = seg[1]
    return param

def write_param(filename, param):
    f = open(filename, 'w')
    for key, value in param.items():
        f.write(key + '=' + value + '\n')
    f.close()


interest_set = ['P10', 'P20', 'P30', 'map']
query_set = ['10', '12', '26', '29', '33', '52', '71', '102', '149', '190', 'all']
param = read_param('Sample.param')
param['fb'] = 'false'
param['fbMu'] = '0'
param['fbDocs'] = '10'
param['fbTerms'] = '50'
param['retrievalAlgorithm'] = 'Indri'
param['fbOrigWeight'] = '0.0'
temp_param_file = 'temp_param.txt'

fbWeightList = ['0.0']
initRankList = ['my-sdm', 'Indri-Sdm.teIn']

f = open('result.txt', 'w')

for initRank in initRankList:
    for fbWeight in fbWeightList:
        param['fbOrigWeight'] = fbWeight
        param['fbInitialRankingFile'] = initRank
        write_param(temp_param_file, param)
        text = os.popen('java -Xmx5g -jar QryEval.jar %s' % temp_param_file).read()
        #print text
        #time = parse_time(text)
        text = os.popen('test.pl').read()
        result = parse_result(text, interest_set)
        print (fbWeight, initRank) #, #time
        print (json.dumps(result['all'], indent = 2))
        for measure in interest_set:
            # print a line
            f.write(fbWeight + '\t' + initRank + '\t' + measure)
            for query in query_set:
                f.write('\t' + result[query][measure])
            f.write('\n')
        #f.write(fbWeight + '\t' + initRank + '\t' + 'time')
        #for query in query_set:
        #    f.write('\t' + time)
        #f.write('\n')
        
f.close()

